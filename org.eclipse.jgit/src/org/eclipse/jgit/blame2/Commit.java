package org.eclipse.jgit.blame2;

import java.util.Comparator;
import java.util.Objects;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

public class Commit {
  public static final Comparator<Commit> TIME_COMPARATOR = Comparator
    .comparingInt(Commit::getTime)
    .thenComparing(Commit::getCommit);

  RevCommit sourceCommit;

  Commit(RevCommit commit) {
    sourceCommit = commit;
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

  PersonIdent getAuthor() {
    return sourceCommit.getAuthorIdent();
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
    Commit that = (Commit) o;
    return Objects.equals(sourceCommit, that.sourceCommit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceCommit);
  }
}
