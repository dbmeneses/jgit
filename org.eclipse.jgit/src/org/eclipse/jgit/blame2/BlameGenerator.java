package org.eclipse.jgit.blame2;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

public class BlameGenerator {
  private final TreeSet<Commit> queue = new TreeSet<>(Commit.TIME_COMPARATOR);
  private final Repository repository;
  private BlameResult blameResult;
  private final Collection<String> filePathsToBlame;
  private final FileMatcher fileMatcher;
  private final FileBlamer fileBlamer;

  /**
   * Revision pool used to acquire commits from.
   */
  private RevWalk revPool;
  private ObjectReader reader;
  private TreeWalk treeWalk;

  public BlameGenerator(Repository repository, BlameResult blameResult, Collection<String> filePathsToBlame) {
    this.repository = repository;
    this.fileMatcher = new FileMatcher(repository);
    this.blameResult = blameResult;
    this.fileBlamer = new FileBlamer(new FileReader(), blameResult);
    this.filePathsToBlame = filePathsToBlame;
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

    RevCommit commit = revPool.parseCommit(head);
    List<FileCandidate> commitFiles = fileMatcher.getCommitFiles(commit)
      .stream()
      .map(f -> new FileCandidate(commit, f.getPath(), f.getObjectId()))
      .collect(Collectors.toList());

    for (FileCandidate fileCandidate : commitFiles) {
      // TODO read number of lines. ALso report sizes to BlameResult?
      fileCandidate.setRegionList();
    }
    
    CommitFileIndex fileIndex = new CommitFileIndex(commitFiles);
    Commit commitCandidate = new Commit(commit, fileIndex);
    push(commitCandidate);

    return this;
  }

  public BlameGenerator push(Commit commitCandidate) {
    // TODO detect nodes already seen
    queue.add(commitCandidate);
    return this;
  }

  public boolean next() throws IOException {
    for (; ; ) {
      Commit n = queue.pollFirst();
      System.out.println("POLL: " + n);
      if (n == null) {
        return done();
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

  public boolean done() {
    revPool.close();
    queue.clear();
    return false;
  }

  private void processOne(Commit n) throws IOException {
    RevCommit parentCommit = n.getParent();
    revPool.parseHeaders(parentCommit);
    // TODO process regions

    Map<String, FileCandidate> parentFilesByPath = fileMatcher.getCommitFiles(parentCommit).stream()
      .map(file -> new FileCandidate(parentCommit, file.getPath(), file.getObjectId()))
      .collect(Collectors.toMap(FileCandidate::getPath, f -> f));

    Commit parentCandidate = push(parentCommit);

    if (n.getCommit() == null) {
      // TODO not sure in what situation this can happen
      return;
    }

    // TODO: detection of rename
  }

  private void processMerge(Commit commitCandidate) {
  }
}
