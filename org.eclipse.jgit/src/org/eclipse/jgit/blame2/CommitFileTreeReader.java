package org.eclipse.jgit.blame2;

import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class CommitFileTreeReader {
  private final Repository repository;

  public CommitFileTreeReader(Repository repository) {
    this.repository = repository;
  }

  /**
   * Find all files in a given commit.
   */
  public List<CommitFile> findFiles(ObjectReader objectReader, RevCommit commit) throws IOException {
    MutableObjectId idBuf = new MutableObjectId();
    List<CommitFile> files = new LinkedList<>();

    TreeWalk treeWalk = new TreeWalk(repository, objectReader);
    treeWalk.setRecursive(true);
    treeWalk.addTree(commit.getTree());

    while (treeWalk.next()) {
      treeWalk.getObjectId(idBuf, 0);
      files.add(new CommitFile(treeWalk.getPathString(), idBuf.toObjectId()));
    }
    return files;
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
