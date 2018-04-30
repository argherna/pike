import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import javax.naming.directory.SearchControls;

final class IO {

  private static int BUF_SZ = 0x1000;

  private static final Logger LOGGER = Logger.getLogger(IO.class.getName());
  
  private IO() {
    // Empty constructor prevents instantiation.
  }

  static String loadUtf8ResourceFromClasspath(String path) throws IOException {
    byte[] resourceBytes = loadResourceFromClasspath(path);
    return new String(resourceBytes, Charset.forName("UTF-8"));
  }

  static byte[] loadResourceFromClasspath(String path) throws IOException {
    LOGGER.fine(() -> {
      return String.format("Loading %s...", path);
    });
    byte[] contents = null;
    InputStream resource = IO.class.getResourceAsStream(path);
    try {
      if (resource == null) {
        LOGGER.fine(String.format(
          "%s not found! Returning empty byte array!", path));
        return new byte[0];
      }
      contents = toByteArray(resource);
    } finally {
      if (resource != null) {
        resource.close();
      }
    }
    LOGGER.fine(() -> {
      return String.format("%s loaded successfully!", path);
    });
    return contents;
  }

  static byte[] toByteArray(InputStream in) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[BUF_SZ];
    while (true) {
      int read = in.read(buf);
      if (read == -1) {
        break;
      }
      bos.write(buf, 0, read);
    }
    return bos.toByteArray();    
  }

  static long lastMTime(String path) throws IOException {
    long lastModifiedTime = -1l;
    try {
      if (IO.class.getResource(path) != null) {
        URI fileUri = IO.class.getResource(path).toURI();
        File resource = null;
        if (fileUri.toASCIIString().startsWith("jar:")) {
          resource = new File(fileUri.toASCIIString().substring(4));
        } else {
          resource = new File(fileUri);
        }
        lastModifiedTime = resource.lastModified();
      }
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    return lastModifiedTime;
  }
}