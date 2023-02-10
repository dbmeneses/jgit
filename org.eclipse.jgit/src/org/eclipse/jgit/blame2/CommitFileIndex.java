package org.eclipse.jgit.blame2;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommitFileIndex {
  private final Map<String, FileCandidate> filesPerPath;

  public CommitFileIndex(List<FileCandidate> files) {
    this.filesPerPath = files.stream().collect(Collectors.toMap(FileCandidate::getPath, f -> f));
  }

  public FileCandidate find(String filePath) {
    return filesPerPath.get(filePath);
  }

  public boolean isEmpty() {
    return filesPerPath.isEmpty();
  }
}
