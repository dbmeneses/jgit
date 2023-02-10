package org.eclipse.jgit.blame2;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

public class BlameResult {
  private final Map<String, FileBlame> fileBlameByPath = new HashMap<>();

  public Collection<FileBlame> getFileBlames() {
    return fileBlameByPath.values();
  }

  public void initialize(String path, int size) {
    fileBlameByPath.put(path, new FileBlame(path, size));
  }

  public void process(RevCommit commit, FileCandidate fileCandidate) {
    PersonIdent srcAuthor = commit.getAuthorIdent();
    int resLine = fileCandidate.getRegionList().resultStart;
    int resEnd = getResultEnd(fileCandidate.getRegionList());

    FileBlame fileBlame = fileBlameByPath.get(fileCandidate.getPath());
    for (; resLine < resEnd; resLine++) {
      fileBlame.commits[resLine] = commit;
      fileBlame.authors[resLine] = srcAuthor;
    }
  }

  private static int getResultEnd(Region r) {
    return r.resultStart + r.length;
  }

  private static class FileBlame {
    private final String path;
    private final RevCommit[] commits;
    private final PersonIdent[] authors;

    public FileBlame(String path, int numberLines) {
      this.path = path;
      this.commits = new RevCommit[numberLines];
      this.authors = new PersonIdent[numberLines];
    }
  }
}
