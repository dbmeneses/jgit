package org.eclipse.jgit.blame2;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.TreeSet;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class BlameGenerator {
  private final TreeSet<StatefulCommit> queue = new TreeSet<>(StatefulCommit.TIME_COMPARATOR);
  private final Repository repository;
  private final BlameResult blameResult;
  private final FileBlamer fileBlamer;
  private final BlobReader blobReader;
  private final StatefulCommitFactory statefulCommitFactory;

  /**
   * Revision pool used to acquire commits from.
   */
  private RevWalk revPool;

  public BlameGenerator(Repository repository, BlameResult blameResult, BlobReader blobReader, StatefulCommitFactory statefulCommitFactory) {
    this.repository = repository;
    this.blameResult = blameResult;
    this.fileBlamer = new FileBlamer(new BlobReader(), blameResult);
    this.blobReader = blobReader;
    this.statefulCommitFactory = statefulCommitFactory;
    initRevPool();
  }

  private void initRevPool() {
    revPool = new RevWalk(repository);
  }

  public BlameGenerator prepareHead() throws NoHeadException, IOException {
    ObjectId head = repository.resolve(Constants.HEAD);
    if (head == null) {
      throw new NoHeadException(MessageFormat.format(JGitText.get().noSuchRefKnown, Constants.HEAD));
    }

    RevCommit headCommit = revPool.parseCommit(head);
    StatefulCommit statefulHeadCommit = statefulCommitFactory.create(headCommit);

    for (FileCandidate fileCandidate : statefulHeadCommit.getFileIndex().getAll()) {
      RawText rawText = blobReader.loadText(revPool.getObjectReader(), fileCandidate.getBlob());

      fileCandidate.setRegionList(new Region(0, 0, rawText.size()));
      blameResult.initialize(fileCandidate.getPath(), rawText.size());
    }

    push(statefulHeadCommit);
    return this;
  }

  public BlameGenerator push(StatefulCommit commitCandidate) {
    // TODO detect nodes already seen
    queue.add(commitCandidate);
    return this;
  }

  public void compute() throws IOException {
    for (; ; ) {
      StatefulCommit n = queue.pollFirst();
      System.out.println("POLL: " + n);
      if (n == null) {
        done();
        return;
      }

      int pCnt = n.getParentCount();
      if (pCnt == 1) {
        processOne(n);
      } else if (pCnt > 1) {
        processMerge(n);
      } else {
        // Root commit, with at least one surviving region. Assign the remaining blame here.
        // TODO
      }
    }
  }

  private boolean done() {
    revPool.close();
    queue.clear();
    return false;
  }

  private void processOne(StatefulCommit n) throws IOException {
    RevCommit parentCommit = n.getParent();
    revPool.parseHeaders(parentCommit);
    StatefulCommit parent = statefulCommitFactory.create(parentCommit);
    fileBlamer.blame(revPool.getObjectReader(), parent, n);

    if (!parent.getFileIndex().isEmpty()) {
      push(parent);
    }

    if (n.getCommit() == null) {
      // TODO not sure in what situation this can happen
      return;
    }

    // TODO: detection of rename
  }

  private void processMerge(StatefulCommit commitCandidate) {
    // TODO
  }
}
