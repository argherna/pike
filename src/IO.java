import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

final class IO {

  private static int BUF_SZ = 0x1000;

  private static final Logger LOGGER = Logger.getLogger(IO.class.getName());
  
  static void sendResponseHeaders(HttpExchange exchange, String contentType, 
    int status, int contentLength) throws IOException {
    Headers h = exchange.getResponseHeaders();
    h.add("Content-Type", contentType);
    h.add("Server", String.format("pike/Java %s", 
      System.getProperty("java.version")));
    exchange.sendResponseHeaders(status, contentLength);
  }

  static String loadUtf8ResourceFromClasspath(String path) throws IOException {
    byte[] resourceBytes = loadResourceFromClasspath(path);
    return new String(resourceBytes, Charset.forName("UTF-8"));
  }

  static byte[] loadResourceFromClasspath(String path) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    LOGGER.fine(() -> {
      return String.format("Loading %s...", path);
    });
    InputStream resource = IO.class.getResourceAsStream(path);
    try {
      if (resource == null) {
        LOGGER.warning(String.format(
          "%s not found! Returning empty byte array!", path));
        return new byte[0];
      }
      byte[] buf = new byte[BUF_SZ];
      while (true) {
        int read = resource.read(buf);
        if (read == -1) {
          break;
        }
        bos.write(buf, 0, read);
      }
    } finally {
      if (resource != null) {
        resource.close();
      }
    }
    LOGGER.fine(() -> {
      return String.format("%s loaded successfully!", path);
    });
    return bos.toByteArray();
  }
}