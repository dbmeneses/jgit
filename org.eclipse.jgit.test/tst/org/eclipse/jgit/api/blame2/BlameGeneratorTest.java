package org.eclipse.jgit.api.blame2;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.blame2.BlameGenerator;
import org.eclipse.jgit.blame2.BlameResult;
import org.eclipse.jgit.blame2.BlobReader;
import org.eclipse.jgit.blame2.CommitFileTreeReader;
import org.eclipse.jgit.blame2.StatefulCommitFactory;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.junit.Test;

public class BlameGeneratorTest {

  @Test
  public void testNewBlameGenerator() throws IOException, NoHeadException {
    Path projectDir = Paths.get("/tmp/proj");

    try (Repository repo = loadRepository(projectDir)) {
      BlameResult result = new BlameResult();
      BlobReader blobReader = new BlobReader();
      StatefulCommitFactory statefulCommitFactory = new StatefulCommitFactory(new CommitFileTreeReader(repo));
      BlameGenerator blameGenerator = new BlameGenerator(repo, result, blobReader, statefulCommitFactory);
      blameGenerator.prepareHead();
      blameGenerator.compute();

      System.out.println("===== results ======");
      for (BlameResult.FileBlame file : result.getFileBlames()) {
        System.out.println(file.getPath());
        for (int i = 0; i < file.lines(); i++) {
          System.out.println("   " + i + " " + file.getAuthors()[i] + " " + file.getCommits()[i]);
        }
      }
    }
  }

  private Repository loadRepository(Path dir) throws IOException {
    return new RepositoryBuilder()
      .findGitDir(dir.toFile())
      .setMustExist(true)
      .build();
  }
}
