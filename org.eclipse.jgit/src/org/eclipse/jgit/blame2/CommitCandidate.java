package org.eclipse.jgit.blame2;

import java.util.Comparator;
import java.util.Objects;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

public class CommitCandidate {
  public static final Comparator<CommitCandidate> TIME_COMPARATOR = Comparator.comparingInt(CommitCandidate::getTime).thenComparing(CommitCandidate::getCommit);

  /** Next candidate in the candidate queue. */
  CommitCandidate queueNext;

  /** Commit being considered (or blamed, depending on state). */
  RevCommit sourceCommit;

  CommitCandidate(Repository repo, RevCommit commit) {
    sourceCommit = commit;
  }

  RevCommit getCommit() {
    return sourceCommit;
  }

  int getParentCount() {
    return sourceCommit.getParentCount();
  }

  RevCommit getParent(int idx) {
    return sourceCommit.getParent(idx);
  }

  int getTime() {
    return sourceCommit.getCommitTime();
  }

  PersonIdent getAuthor() {
    return sourceCommit.getAuthorIdent();
  }

  CommitCandidate create(Repository repo, RevCommit commit) {
    return new CommitCandidate(repo, commit);
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
    CommitCandidate that = (CommitCandidate) o;
    return Objects.equals(sourceCommit, that.sourceCommit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceCommit);
  }
}
