package org.eclipse.jgit.blame2;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.TreeSet;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

public class RepoBlameGenerator {
  private final DiffAlgorithm diffAlgorithm = new HistogramDiff();
  private final RawTextComparator textComparator = RawTextComparator.DEFAULT;
  private final TreeSet<CommitCandidate> queue = new TreeSet<>(CommitCandidate.TIME_COMPARATOR);
  private final Repository repository;
  private final Collection<String> filePathsToBlame;
  private final MutableObjectId idBuf;
  /**
   * Revision pool used to acquire commits from.
   */
  private RevWalk revPool;
  private ObjectReader reader;
  private TreeWalk treeWalk;

  public RepoBlameGenerator(Repository repository, Collection<String> filePathsToBlame) {
    this.repository = repository;
    this.filePathsToBlame = filePathsToBlame;
    this.idBuf = new MutableObjectId();
    initRevPool();
  }

  private void initRevPool() {
    revPool = new RevWalk(repository);
    reader = revPool.getObjectReader();
    treeWalk = new TreeWalk(reader);
    treeWalk.setRecursive(true);
  }

  public RepoBlameGenerator prepareHead() throws NoHeadException, IOException {
    ObjectId head = repository.resolve(Constants.HEAD);
    if (head == null) {
      throw new NoHeadException(MessageFormat.format(JGitText.get().noSuchRefKnown, Constants.HEAD));
    }
    push(head);
    return this;
  }

  public RepoBlameGenerator push(AnyObjectId id) throws IOException {
    RevCommit commit = revPool.parseCommit(id);
    push(new CommitCandidate(repository, commit));
    return this;
  }

  public RepoBlameGenerator push(CommitCandidate commitCandidate) {
    if (queue.contains(commitCandidate)) {
      // TODO
      throw new IllegalStateException();
    }

    queue.add(commitCandidate);
    return this;
  }

  public boolean next() throws IOException {
    // TODO process regions
    for (; ; ) {
      CommitCandidate n = queue.pollFirst();
      System.out.println("POLL: " + n);
      if (n == null) {
        return done();
      }

      int pCnt = n.getParentCount();
      if (pCnt == 1) {
        if (processOne(n)) {
          return true;
        }

      } else if (pCnt > 1) {
        if (processMerge(n)) {
          return true;
        }

      } else {
        // Root commit, with at least one surviving region.
        // Assign the remaining blame here.
        // TODO
      }
    }
  }

  public boolean done() {
    revPool.close();
    queue.clear();
    return false;
  }

  private boolean processOne(CommitCandidate n) throws IOException {
    RevCommit parent = n.getParent(0);
    if (parent == null) {
      // TODO
      //return split(n.getNextCandidate(0), n);
      return false;
    }
    revPool.parseHeaders(parent);
    push(parent);

    if (n.sourceCommit == null) {
      // TODO
      //return result(n);
      return false;
    }

    // TODO: detection of rename
    return true;
  }

  private boolean processMerge(CommitCandidate commitCandidate) {
    return true;
  }
}
