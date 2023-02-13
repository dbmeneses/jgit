package org.eclipse.jgit.api.blame;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.blame2.CommitFileTreeReader;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
  public void testOldImplementation() throws IOException, GitAPIException {
    Path projectDir = Paths.get("C:\\Users\\meneses\\git\\sonar-enterprise").toAbsolutePath();

    try (Repository repo = loadRepository(projectDir)) {
      Collection<String> paths = readFiles(repo);
      for (String p : paths) {
        System.out.println(p);
        BlameResult blameResult = Git.wrap(repo).blame()
                // Equivalent to -w command line option
                .setTextComparator(RawTextComparator.WS_IGNORE_ALL)
                .setFilePath(p).call();
      }
    }
  }

  private Collection<String> readFiles(Repository repository) throws IOException {
    CommitFileTreeReader treeReader = new CommitFileTreeReader(repository);
    RevCommit head = repository.parseCommit(repository.resolve(Constants.HEAD));
    return treeReader.findFiles(repository.newObjectReader(), head).stream().map(CommitFileTreeReader.CommitFile::getPath)
            .collect(Collectors.toList());
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
