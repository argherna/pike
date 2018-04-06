import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import javax.naming.directory.SearchControls;

final class IO {

  private static int BUF_SZ = 0x1000;

  private static final Logger LOGGER = Logger.getLogger(IO.class.getName());
  
  private IO (){
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
}