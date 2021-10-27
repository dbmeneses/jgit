/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class DfsBlockCacheTest {
	@Rule
	public TestName testName = new TestName();
	private TestRng rng;
	private DfsBlockCache cache;

	@Before
	public void setUp() {
		rng = new TestRng(testName.getMethodName());
		resetCache();
	}

	@SuppressWarnings("resource")
	@Test
	public void streamKeyReusesBlocks() throws Exception {
		DfsRepositoryDescription repo = new DfsRepositoryDescription("test");
		InMemoryRepository r1 = new InMemoryRepository(repo);
		byte[] content = rng.nextBytes(424242);
		ObjectId id;
		try (ObjectInserter ins = r1.newObjectInserter()) {
			id = ins.insert(OBJ_BLOB, content);
			ins.flush();
		}

		long oldSize = LongStream.of(cache.getCurrentSize()).sum();
		assertTrue(oldSize > 2000);
		assertEquals(0, LongStream.of(cache.getHitCount()).sum());

		List<DfsPackDescription> packs = r1.getObjectDatabase().listPacks();
		InMemoryRepository r2 = new InMemoryRepository(repo);
		r2.getObjectDatabase().commitPack(packs, Collections.emptyList());
		try (ObjectReader rdr = r2.newObjectReader()) {
			byte[] actual = rdr.open(id, OBJ_BLOB).getBytes();
			assertTrue(Arrays.equals(content, actual));
		}
		assertEquals(0, LongStream.of(cache.getMissCount()).sum());
		assertEquals(oldSize, LongStream.of(cache.getCurrentSize()).sum());
	}

	@SuppressWarnings("resource")
	@Test
	public void weirdBlockSize() throws Exception {
		DfsRepositoryDescription repo = new DfsRepositoryDescription("test");
		InMemoryRepository r1 = new InMemoryRepository(repo);

		byte[] content1 = rng.nextBytes(4);
		byte[] content2 = rng.nextBytes(424242);
		ObjectId id1;
		ObjectId id2;
		try (ObjectInserter ins = r1.newObjectInserter()) {
			id1 = ins.insert(OBJ_BLOB, content1);
			id2 = ins.insert(OBJ_BLOB, content2);
			ins.flush();
		}

		resetCache();
		List<DfsPackDescription> packs = r1.getObjectDatabase().listPacks();

		InMemoryRepository r2 = new InMemoryRepository(repo);
		r2.getObjectDatabase().setReadableChannelBlockSizeForTest(500);
		r2.getObjectDatabase().commitPack(packs, Collections.emptyList());
		try (ObjectReader rdr = r2.newObjectReader()) {
			byte[] actual = rdr.open(id1, OBJ_BLOB).getBytes();
			assertTrue(Arrays.equals(content1, actual));
		}

		InMemoryRepository r3 = new InMemoryRepository(repo);
		r3.getObjectDatabase().setReadableChannelBlockSizeForTest(500);
		r3.getObjectDatabase().commitPack(packs, Collections.emptyList());
		try (ObjectReader rdr = r3.newObjectReader()) {
			byte[] actual = rdr.open(id2, OBJ_BLOB).getBytes();
			assertTrue(Arrays.equals(content2, actual));
		}
	}

	@SuppressWarnings("resource")
	@Test
	public void hasCacheHotMap() throws Exception {
		Map<PackExt, Integer> cacheHotMap = new HashMap<>();
		// Pack index will be kept in cache longer.
		cacheHotMap.put(PackExt.INDEX, Integer.valueOf(3));
		DfsBlockCache.reconfigure(new DfsBlockCacheConfig().setBlockSize(512)
				.setBlockLimit(512 * 4).setCacheHotMap(cacheHotMap));
		cache = DfsBlockCache.getInstance();

		DfsRepositoryDescription repo = new DfsRepositoryDescription("test");
		InMemoryRepository r1 = new InMemoryRepository(repo);
		byte[] content = rng.nextBytes(424242);
		ObjectId id;
		try (ObjectInserter ins = r1.newObjectInserter()) {
			id = ins.insert(OBJ_BLOB, content);
			ins.flush();
		}

		try (ObjectReader rdr = r1.newObjectReader()) {
			byte[] actual = rdr.open(id, OBJ_BLOB).getBytes();
			assertTrue(Arrays.equals(content, actual));
		}
		// All cache entries are hot and cache is at capacity.
		assertTrue(LongStream.of(cache.getHitCount()).sum() > 0);
		assertEquals(99, cache.getFillPercentage());

		InMemoryRepository r2 = new InMemoryRepository(repo);
		content = rng.nextBytes(424242);
		try (ObjectInserter ins = r2.newObjectInserter()) {
			ins.insert(OBJ_BLOB, content);
			ins.flush();
		}
		assertEquals(0, LongStream.of(cache.getMissCount()).sum());
		assertTrue(cache.getEvictions()[PackExt.PACK.getPosition()] > 0);
		assertEquals(0, cache.getEvictions()[PackExt.INDEX.getPosition()]);
	}

	@SuppressWarnings("resource")
	@Test
	public void noConcurrencySerializedReads() throws Exception {
		DfsRepositoryDescription repo = new DfsRepositoryDescription("test");
		InMemoryRepository r1 = new InMemoryRepository(repo);
		TestRepository<InMemoryRepository> repository = new TestRepository<>(
				r1);
		RevCommit commit = repository.branch("/refs/ref1").commit()
				.add("blob1", "blob1").create();
		repository.branch("/refs/ref2").commit().add("blob2", "blob2")
				.parent(commit).create();

		new DfsGarbageCollector(r1).pack(null);
		// Reset cache with concurrency Level at 1 i.e. no concurrency.
		DfsBlockCache.reconfigure(new DfsBlockCacheConfig().setBlockSize(512)
				.setBlockLimit(1 << 20).setConcurrencyLevel(1));
		cache = DfsBlockCache.getInstance();

		DfsReader reader = (DfsReader) r1.newObjectReader();
		ExecutorService pool = Executors.newFixedThreadPool(10);
		for (DfsPackFile pack : r1.getObjectDatabase().getPacks()) {
			// Only load non-garbage pack with bitmap.
			if (pack.isGarbage()) {
				continue;
			}
			asyncRun(pool, () -> pack.getBitmapIndex(reader));
			asyncRun(pool, () -> pack.getPackIndex(reader));
			asyncRun(pool, () -> pack.getBitmapIndex(reader));
		}

		pool.shutdown();
		pool.awaitTermination(500, TimeUnit.MILLISECONDS);
		assertTrue("Threads did not complete, likely due to a deadlock.",
				pool.isTerminated());
		assertEquals(1, cache.getMissCount()[PackExt.BITMAP_INDEX.ordinal()]);
		assertEquals(1, cache.getMissCount()[PackExt.INDEX.ordinal()]);
	}

	private void resetCache() {
		DfsBlockCache.reconfigure(new DfsBlockCacheConfig().setBlockSize(512)
				.setBlockLimit(1 << 20));
		cache = DfsBlockCache.getInstance();
	}

	private void asyncRun(ExecutorService pool, Callable<?> call) {
		pool.execute(() -> {
			try {
				call.call();
			} catch (Exception e) {
				// Ignore.
			}
		});
	}
}
