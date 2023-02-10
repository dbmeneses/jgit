package org.eclipse.jgit.blame2;

import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectReader;

public class FileBlamer {
  private final BlobReader fileReader;
  private final DiffAlgorithm diffAlgorithm = new HistogramDiff();
  private final RawTextComparator textComparator = RawTextComparator.DEFAULT;
  private final BlameResult blameResult;

  public FileBlamer(BlobReader fileReader, BlameResult blameResult) {
    this.fileReader = fileReader;
    this.blameResult = blameResult;
  }

  public void blame(ObjectReader objectReader, StatefulCommit parent, StatefulCommit source) {
    for (FileCandidate sourceFile : source.getFileIndex().getAll()) {
      FileCandidate parentFile = parent.getFileIndex().get(sourceFile.getPath());
      if (parentFile != null) {
        splitBlameWithParent(objectReader, parentFile, sourceFile);
      }

      if (sourceFile.getRegionList() != null) {
        blameResult.process(source.getCommit(), sourceFile);
      }
    }

    parent.getFileIndex().removeFilesWithoutRegions();
  }

  public void splitBlameWithParent(ObjectReader objectReader, FileCandidate parent, FileCandidate source) {
    EditList editList = diffAlgorithm.diff(textComparator,
      fileReader.loadText(objectReader, parent.getBlob()),
      fileReader.loadText(objectReader, source.getBlob()));
    if (editList.isEmpty()) {
      // Ignoring whitespace (or some other special comparator) can cause non-identical blobs to have an empty edit list
      parent.setRegionList(source.getRegionList());
      source.setRegionList(null);
      return;
    }

    parent.takeBlame(editList, source);
  }
}
