package org.eclipse.jgit.blame2;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

public class FileMatcher {
  private final Repository repository;

  public FileMatcher(Repository repository) {
    this.repository = repository;
  }

  public List<FileMatch> match(RevCommit c1, RevCommit c2) throws IOException {
    Map<String, CommitFile> c1Files = getCommitFiles(c1)
      .stream().collect(Collectors.toMap(CommitFile::getPath, f -> f));
    Map<String, CommitFile> c2Files = getCommitFiles(c2)
      .stream().collect(Collectors.toMap(CommitFile::getPath, f -> f));

    List<FileMatch> matches = new LinkedList<>();
    for (Map.Entry<String, CommitFile> e : c1Files.entrySet()) {
      CommitFile f2 = c2Files.get(e.getKey());
      matches.add(new FileMatch(e.getValue(), f2));
    }

    return matches;
  }

  public List<CommitFile> getCommitFiles(RevCommit commit) throws IOException {
    MutableObjectId idBuf = new MutableObjectId();
    List<CommitFile> files = new LinkedList<>();

    try (ObjectReader objectReader = repository.newObjectReader()) {
      TreeWalk treeWalk = new TreeWalk(repository, objectReader);
      treeWalk.setRecursive(true);
      treeWalk.addTree(commit.getTree());

      while (treeWalk.next()) {
        treeWalk.getObjectId(idBuf, 0);
        files.add(new CommitFile(treeWalk.getPathString(), idBuf.toObjectId()));
      }
    }
    return files;
  }

  public static class FileMatch {
    private final CommitFile file;
    private final CommitFile parentFile;

    public FileMatch(CommitFile file, CommitFile parentFile) {
      this.file = file;
      this.parentFile = parentFile;
    }

    public CommitFile getFile() {
      return file;
    }

    public CommitFile getParentFile() {
      return parentFile;
    }
  }

  public static class CommitFile {
    private final String path;
    private final ObjectId objectId;

    public CommitFile(String path, ObjectId objectId) {
      this.path = path;
      this.objectId = objectId;
    }

    public String getPath() {
      return path;
    }

    public ObjectId getObjectId() {
      return objectId;
    }
  }
}
