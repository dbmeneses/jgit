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
public class FileReader {
  public RawText loadText(ObjectReader objectReader, ObjectId objectId) {
    try {
      // TODO applySmudgeFilter?
      ObjectLoader open = objectReader.open(objectId, Constants.OBJ_BLOB);
      return new RawText(open.getCachedBytes(Integer.MAX_VALUE));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
