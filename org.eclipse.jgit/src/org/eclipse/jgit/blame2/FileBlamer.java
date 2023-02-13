package org.eclipse.jgit.blame2;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class FileBlamer {
  private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
  private final BlobReader fileReader;
  private final DiffAlgorithm diffAlgorithm = new HistogramDiff();
  private final RawTextComparator textComparator = RawTextComparator.DEFAULT;
  private final BlameResult blameResult;

  public FileBlamer(BlobReader fileReader, BlameResult blameResult) {
    this.fileReader = fileReader;
    this.blameResult = blameResult;
  }

  public void blameLastCommit(StatefulCommit source) {
    for (FileCandidate sourceFile : source.getFileIndex().getAll()) {
      if (sourceFile.getRegionList() != null) {
        blameResult.process(source.getCommit(), sourceFile);
      }
    }
  }

  public void blame(ObjectReader objectReader, StatefulCommit parent, StatefulCommit source) {
    List<Future<Void>> tasks = new ArrayList<>();
    for (FileCandidate sourceFile : source.getFileIndex().getAll()) {
      tasks.add(executor.submit(() -> blameFile(objectReader, sourceFile, parent, source.getCommit())));
    }
    try {
      for (Future<Void> f : tasks) {
        f.get(1, TimeUnit.HOURS);
      }
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new IllegalStateException(e);
    }

    parent.getFileIndex().removeFilesWithoutRegions();
  }

  private Void blameFile(ObjectReader objectReader, FileCandidate sourceFile, StatefulCommit parent, RevCommit commit) {
    FileCandidate parentFile = fileMatchingFileFromParent(sourceFile, parent.getFileIndex());

    if (parentFile != null) {
      splitBlameWithParent(objectReader, parentFile, sourceFile);
    }

    if (sourceFile.getRegionList() != null) {
      blameResult.process(commit, sourceFile);
    }
    return null;
  }

  @Nullable
  private FileCandidate fileMatchingFileFromParent(FileCandidate file, CommitFileIndex parentFileIndex) {
    // TODO file rename detection
    return parentFileIndex.get(file.getPath());
  }

  public void splitBlameWithParent(ObjectReader objectReader, FileCandidate parent, FileCandidate source) {
    // the ObjectReader is not thread safe, so we need to clone it
    // TODO could the fact that we are not using a common ObjectReader be causing bad performance
    //  when reading the file contents?
    ObjectReader reader = objectReader.newReader();
    EditList editList = diffAlgorithm.diff(textComparator,
      fileReader.loadText(reader, parent.getBlob()),
      fileReader.loadText(reader, source.getBlob()));
    if (editList.isEmpty()) {
      // Ignoring whitespace (or some other special comparator) can cause non-identical blobs to have an empty edit list
      parent.setRegionList(source.getRegionList());
      source.setRegionList(null);
      return;
    }

    parent.takeBlame(editList, source);
  }
}
