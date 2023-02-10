package org.eclipse.jgit.api.blame;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.blame2.BlameGenerator;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

public class BlameTest {
  @Test
  public void test() throws IOException, GitAPIException {
    Path projectDir = Paths.get("/tmp/proj");

    try (Repository repo = loadRepository(projectDir)) {
      Git git = Git.wrap(repo);
      BlameResult file = git.blame().setFilePath("file").call();
      file.computeAll();
      for (int i = 0; i < file.getResultContents().size(); i++) {
        System.out.println(file.getSourceLine(i) + " " + file.getSourceCommit(i) + " " + file.getSourceAuthor(i).getEmailAddress());
      }
    }
  }

  @Test
  public void testRepoBlameGenerator() throws IOException, NoHeadException {
    Path projectDir = Paths.get("/tmp/proj");
    try (Repository repo = loadRepository(projectDir)) {

      Collection<String> files = List.of("file");
      BlameGenerator repoBlameGenerator = new BlameGenerator(repo, files);
      repoBlameGenerator.prepareHead();

      while (repoBlameGenerator.next()) {
      }

    }
  }

  @Test
  public void testTreeWalk() throws IOException {
    Path projectDir = Paths.get("/tmp/proj");

    try (Repository repo = loadRepository(projectDir)) {
      Ref head = repo.exactRef("HEAD");
      if (head == null) {
        throw new IOException("HEAD reference not found");
      }
      RevCommit headCommit = repo.parseCommit(head.getObjectId());

      processTree(repo, headCommit, (path, rawText) -> {
        System.out.println(path);
        System.out.println("======= contents =======");
        for (int i = 0; i < rawText.size(); i++) {
          System.out.println(rawText.getString(i));
        }
      });
    }
  }

  public void processTree(Repository repo, RevCommit commit, FileProcessor fileProcessor) throws IOException {
    // TODO cache it?
    MutableObjectId idBuf = new MutableObjectId();

    try (ObjectReader objectReader = repo.newObjectReader()) {
      TreeWalk treeWalk = new TreeWalk(repo, objectReader);
      treeWalk.setRecursive(true);
      treeWalk.addTree(commit.getTree());

      while (treeWalk.next()) {
        treeWalk.getObjectId(idBuf, 0);
        Supplier<RawText> rawText = () -> loadText(objectReader, idBuf.toObjectId());
        fileProcessor.process(treeWalk.getPathString(), rawText);
      }
    }
  }

  private interface FileProcessor {
    void process(String path, Supplier<RawText> rawText);
  }

  private RawText loadText(ObjectReader reader, ObjectId objectId) {
    try {
    // TODO applySmudgeFilter?
    ObjectLoader open = reader.open(objectId, Constants.OBJ_BLOB);
    return new RawText(open.getCachedBytes(Integer.MAX_VALUE));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Repository loadRepository(Path dir) throws IOException {
    return new RepositoryBuilder()
      .findGitDir(dir.toFile())
      .setMustExist(true)
      .build();
  }
}
