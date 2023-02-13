package org.eclipse.jgit.blame2;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;

public class StatefulCommitFactory {
  private final CommitFileTreeReader commitFileTree;

  public StatefulCommitFactory(CommitFileTreeReader commitFileTree) {
    this.commitFileTree = commitFileTree;
  }

  public StatefulCommit create(ObjectReader objectReader, RevCommit commit) throws IOException {
    List<FileCandidate> commitFiles = commitFileTree.findFiles(objectReader, commit)
      .stream()
      .map(f -> new FileCandidate(commit, f.getPath(), f.getObjectId()))
      .collect(Collectors.toList());
    CommitFileIndex fileIndex = new CommitFileIndex(commitFiles);
    return new StatefulCommit(commit, fileIndex);
  }
}
