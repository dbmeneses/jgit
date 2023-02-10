package org.eclipse.jgit.blame2;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jgit.annotations.Nullable;

public class CommitFileIndex {
  private final Map<String, FileCandidate> filesPerPath;

  public CommitFileIndex(List<FileCandidate> files) {
    this.filesPerPath = files.stream().collect(Collectors.toMap(FileCandidate::getPath, f -> f));
  }

  @Nullable
  public FileCandidate get(String filePath) {
    return filesPerPath.get(filePath);
  }

  public boolean isEmpty() {
    return filesPerPath.isEmpty();
  }

  public Collection<FileCandidate> getAll() {
    return filesPerPath.values();
  }

  public void removeFilesWithoutRegions() {
    filesPerPath.entrySet().removeIf(next -> next.getValue().getRegionList() == null);
  }
}
