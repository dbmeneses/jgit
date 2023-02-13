/*
 * Copyright (C) 2011, 2019 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.blame2;

import java.io.IOException;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A source that may have supplied some (or all) of the result file.
 * <p>
 * Candidates are kept in a queue by BlameGenerator, allowing the generator to perform a parallel search down the parents of any merges that are discovered
 * during the history traversal. Each candidate retains a {@link #regionList} describing sections of the result file the candidate has taken responsibility
 * for either directly or indirectly through its history. Actual blame from this region list will be assigned to the candidate when its ancestor commit(s) are
 * themselves converted into Candidate objects and the ancestor's candidate uses {@link #takeBlame(EditList, FileCandidate)} to accept responsibility for sections
 * of the result.
 */
class FileCandidate {
	/** Commit being considered (or blamed, depending on state). */
	private final RevCommit sourceCommit;
	/** Path of the candidate file in {@link #sourceCommit}. */
	private final String sourcePath;
	/** Unique name of the candidate blob in {@link #sourceCommit}. */
	private final ObjectId sourceBlob;
	/**
	 * Chain of regions this candidate may be blamed for. This list is always kept sorted by resultStart order,
	 * making it simple to merge-join with the sorted EditList during blame assignment.
	 */
	private Region regionList;

	FileCandidate(RevCommit commit, String path, ObjectId blob) {
		sourceCommit = commit;
		sourcePath = path;
		sourceBlob = blob;
	}

	public RevCommit getCommit() {
		return sourceCommit;
	}

	public ObjectId getBlob() {
		return sourceBlob;
	}

	@Nullable
	public Region getRegionList() {
		return regionList;
	}

	public String getPath() {
		return sourcePath;
	}

	public void setRegionList(@Nullable Region regionList) {
		this.regionList = regionList;
	}

	void beginResult(RevWalk rw) throws IOException {
		rw.parseBody(sourceCommit);
	}


	void takeBlame(EditList editList, FileCandidate child) {
		blame(editList, this, child);
	}

	private static void blame(EditList editList, FileCandidate a, FileCandidate b) {
		Region r = b.clearRegionList();
		Region aTail = null;
		Region bTail = null;

		for (int eIdx = 0; eIdx < editList.size();) {
			// If there are no more regions left, neither side has any more responsibility for the result. Remaining edits can
			// be safely ignored.
			if (r == null)
				return;

			Edit e = editList.get(eIdx);

			// Edit ends before the next candidate region. Skip the edit.
			if (e.getEndB() <= r.sourceStart) {
				eIdx++;
				continue;
			}

			// Next candidate region starts before the edit. Assign some of the blame onto A, but possibly split and also on B.
			if (r.sourceStart < e.getBeginB()) {
				int d = e.getBeginB() - r.sourceStart;
				if (r.length <= d) {
					// Pass the blame for this region onto A.
					Region next = r.next;
					r.sourceStart = e.getBeginA() - d;
					aTail = add(aTail, a, r);
					r = next;
					continue;
				}

				// Split the region and assign some to A, some to B.
				aTail = add(aTail, a, r.splitFirst(e.getBeginA() - d, d));
				r.slideAndShrink(d);
			}

			// At this point e.getBeginB() <= r.sourceStart.

			// An empty edit on the B side isn't relevant to this split, as it does not overlap any candidate region.
			if (e.getLengthB() == 0) {
				eIdx++;
				continue;
			}

			// If the region ends before the edit, blame on B.
			int rEnd = r.sourceStart + r.length;
			if (rEnd <= e.getEndB()) {
				Region next = r.next;
				bTail = add(bTail, b, r);
				r = next;
				if (rEnd == e.getEndB())
					eIdx++;
				continue;
			}

			// This region extends beyond the edit. Blame the first half of the region on B, and process the rest after.
			int len = e.getEndB() - r.sourceStart;
			bTail = add(bTail, b, r.splitFirst(r.sourceStart, len));
			r.slideAndShrink(len);
			eIdx++;
		}

		if (r == null)
			return;

		// For any remaining region, pass the blame onto A after shifting the source start to account for the difference between the two.
		Edit e = editList.get(editList.size() - 1);
		int endB = e.getEndB();
		int d = endB - e.getEndA();
		if (aTail == null)
			a.regionList = r;
		else
			aTail.next = r;
		do {
			if (endB <= r.sourceStart)
				r.sourceStart -= d;
			r = r.next;
		} while (r != null);
	}

	private static Region add(Region aTail, FileCandidate a, Region n) {
		// If there is no region on the list, use only this one.
		if (aTail == null) {
			a.regionList = n;
			n.next = null;
			return n;
		}

		// If the prior region ends exactly where the new region begins in both the result and the source, combine these together into
		// one contiguous region. This occurs when intermediate commits have inserted and deleted lines in the middle of a region. Try
		// to report this region as a single region to the application, rather than in fragments.
		if (aTail.resultStart + aTail.length == n.resultStart && aTail.sourceStart + aTail.length == n.sourceStart) {
			aTail.length += n.length;
			return aTail;
		}

		// Append the region onto the end of the list.
		aTail.next = n;
		n.next = null;
		return n;
	}

	private Region clearRegionList() {
		Region r = regionList;
		regionList = null;
		return r;
	}

	boolean canMergeRegions(FileCandidate other) {
		return sourceCommit == other.sourceCommit && sourcePath.equals(other.sourcePath);
	}

	void mergeRegions(FileCandidate other) {
		// regionList is always sorted by resultStart. Merge join two linked lists, preserving the ordering. Combine neighboring
		// regions to reduce the number of results seen by callers.
		Region a = clearRegionList();
		Region b = other.clearRegionList();
		Region t = null;

		while (a != null && b != null) {
			if (a.resultStart < b.resultStart) {
				Region n = a.next;
				t = add(t, this, a);
				a = n;
			} else {
				Region n = b.next;
				t = add(t, this, b);
				b = n;
			}
		}

		if (a != null) {
			Region n = a.next;
			t = add(t, this, a);
			t.next = n;
		} else /* b != null */{
			Region n = b.next;
			t = add(t, this, b);
			t.next = n;
		}
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		r.append("Candidate[");
		r.append(sourcePath);
		if (sourceCommit != null)
			r.append(" @ ").append(sourceCommit.abbreviate(6).name());
		if (regionList != null)
			r.append(" regions:").append(regionList);
		r.append("]");
		return r.toString();
	}
}
