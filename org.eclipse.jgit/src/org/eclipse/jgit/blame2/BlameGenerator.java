package org.eclipse.jgit.blame2;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.TreeSet;
import org.eclipse.jgit.api.errors.NoHeadException;
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

public class BlameGenerator {
  private final TreeSet<Commit> queue = new TreeSet<>(Commit.TIME_COMPARATOR);
  private final Repository repository;
  private final Collection<String> filePathsToBlame;
  private final MutableObjectId idBuf;
  private Commit currentNode = null;
  /**
   * Revision pool used to acquire commits from.
   */
  private RevWalk revPool;
  private ObjectReader reader;
  private TreeWalk treeWalk;

  public BlameGenerator(Repository repository, Collection<String> filePathsToBlame) {
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

  public BlameGenerator prepareHead() throws NoHeadException, IOException {
    ObjectId head = repository.resolve(Constants.HEAD);
    if (head == null) {
      throw new NoHeadException(MessageFormat.format(JGitText.get().noSuchRefKnown, Constants.HEAD));
    }
    currentNode = push(head);
    return this;
  }

  public Commit push(AnyObjectId id) throws IOException {
    RevCommit commit = revPool.parseCommit(id);
    Commit commitCandidate = new Commit(commit);
    push(commitCandidate);
    return commitCandidate;
  }

  public BlameGenerator push(Commit commitCandidate) {
    // TODO detect nodes already seen
    queue.add(commitCandidate);
    return this;
  }

  public boolean next() throws IOException {
    // TODO process regions
    for (; ; ) {
      Commit n = queue.pollFirst();
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

  private boolean processOne(Commit n) throws IOException {
    RevCommit parentCommit = n.getParent();
    if (parentCommit == null) {
      // TODO
      //return split(n.getNextCandidate(0), n);
      return false;
    }
    revPool.parseHeaders(parentCommit);
    Commit parentCandidate = push(parentCommit);

    if (n.sourceCommit == null) {
      // TODO
      //return result(n);
      return false;
    }

    // TODO: detection of rename
    return true;
  }

  private boolean processMerge(Commit commitCandidate) {
    return true;
  }
}
