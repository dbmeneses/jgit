package org.eclipse.jgit.blame2;

import java.util.Map;
import org.eclipse.jgit.revwalk.RevCommit;

public class CommitFileIndex {
  private RevCommit commit;
  private Map<String, FileCandidate> filesPerPath;

  public FileCandidate find(String filePath) {
    return filesPerPath.get(filePath);
  }

  public RevCommit getCommit() {
    return commit;
  }

  public boolean isEmpty() {
    return filesPerPath.isEmpty();
  }
}
