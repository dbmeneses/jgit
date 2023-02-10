package org.eclipse.jgit.blame2;

import java.util.Comparator;
import java.util.Objects;
import org.eclipse.jgit.revwalk.RevCommit;

public class StatefulCommit {
  public static final Comparator<StatefulCommit> TIME_COMPARATOR = Comparator
    .comparingInt(StatefulCommit::getTime)
    .thenComparing(StatefulCommit::getCommit);

  private final RevCommit sourceCommit;
  private final CommitFileIndex fileIndex;

  StatefulCommit(RevCommit commit, CommitFileIndex fileIndex) {
    this.sourceCommit = commit;
    this.fileIndex = fileIndex;
  }

  CommitFileIndex getFileIndex() {
    return fileIndex;
  }

  RevCommit getCommit() {
    return sourceCommit;
  }

  int getParentCount() {
    return sourceCommit.getParentCount();
  }

  RevCommit getParent() {
    return sourceCommit.getParent(0);
  }

  int getTime() {
    return sourceCommit.getCommitTime();
  }

  /** {@inheritDoc} */
  @SuppressWarnings("nls")
  @Override
  public String toString() {
    StringBuilder r = new StringBuilder();
    r.append("Candidate[");
    if (sourceCommit != null) {
      r.append(" @ ").append(sourceCommit.abbreviate(6).name());
    }
    r.append("]");
    return r.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StatefulCommit that = (StatefulCommit) o;
    return Objects.equals(sourceCommit, that.sourceCommit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceCommit);
  }
}
