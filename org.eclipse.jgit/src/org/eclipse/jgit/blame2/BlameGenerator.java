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
  // TODO is it worth to use its object reader everywhere or only in certain cases? What exactly is cached?
  private final RevWalk revPool;

  public BlameGenerator(Repository repository, BlameResult blameResult, BlobReader blobReader, StatefulCommitFactory statefulCommitFactory) {
    this.repository = repository;
    this.blameResult = blameResult;
    this.fileBlamer = new FileBlamer(new BlobReader(), blameResult);
    this.blobReader = blobReader;
    this.statefulCommitFactory = statefulCommitFactory;
    this.revPool = new RevWalk(repository);
  }

  private void prepareHead() throws NoHeadException, IOException {
    ObjectId head = repository.resolve(Constants.HEAD);
    if (head == null) {
      throw new NoHeadException(MessageFormat.format(JGitText.get().noSuchRefKnown, Constants.HEAD));
    }

    RevCommit headCommit = revPool.parseCommit(head);
    StatefulCommit statefulHeadCommit = statefulCommitFactory.create(revPool.getObjectReader(), headCommit);

    // Read all file's contents to get the number of lines in each file. With that, we can initialize regions and
    // also the arrays that will contain the blame results
    for (FileCandidate fileCandidate : statefulHeadCommit.getFileIndex().getAll()) {
      RawText rawText = blobReader.loadText(revPool.getObjectReader(), fileCandidate.getBlob());

      fileCandidate.setRegionList(new Region(0, 0, rawText.size()));
      blameResult.initialize(fileCandidate.getPath(), rawText.size());
    }

    push(statefulHeadCommit);
  }

  private void push(StatefulCommit commitCandidate) {
    // TODO detect nodes that were already seen
    queue.add(commitCandidate);
  }

  public void compute() throws IOException, NoHeadException {
    prepareHead();

    for (; ; ) {
      StatefulCommit current = queue.pollFirst();
      if (current == null) {
        close();
        return;
      }
      System.out.println("POLL: " + current + " Files left to blame: " + current.getFileIndex().getAll().size());

      int pCnt = current.getParentCount();
      if (pCnt == 1) {
        processOne(current);
      } else if (pCnt > 1) {
        processMerge(current);
      } else {
        // no more parents, so blame all remaining regions to the current commit
        fileBlamer.blameLastCommit(current);
      }
    }
  }

  private void processOne(StatefulCommit current) throws IOException {
    RevCommit parentCommit = current.getParent();
    revPool.parseHeaders(parentCommit);
    StatefulCommit parent = statefulCommitFactory.create(revPool.getObjectReader(), parentCommit);
    fileBlamer.blame(revPool.getObjectReader(), parent, current);

    if (!parent.getFileIndex().isEmpty()) {
      push(parent);
    }

    if (current.getCommit() == null) {
      // TODO not sure in what situation this can happen
      return;
    }
  }

  private void processMerge(StatefulCommit commitCandidate) {
    // TODO
  }

  private void close() {
    revPool.close();
    queue.clear();
  }
}
