package org.eclipse.jgit.blame2;

import java.io.IOException;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;

/**
 * Reads the contents of a BLOB object (typically a file)
 */
public class FileReader implements AutoCloseable {
  private ObjectReader objectReader;

  public FileReader(ObjectReader objectReader) {
    this.objectReader = objectReader;
  }

  public RawText loadText(ObjectId objectId) {
    try {
      // TODO applySmudgeFilter?
      ObjectLoader open = objectReader.open(objectId, Constants.OBJ_BLOB);
      return new RawText(open.getCachedBytes(Integer.MAX_VALUE));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void close() {
    if (objectReader != null) {
      objectReader.close();
      objectReader = null;
    }
  }
}
