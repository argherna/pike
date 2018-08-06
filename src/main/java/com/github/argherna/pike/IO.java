package com.github.argherna.pike;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

final class IO {

  private static int BUF_SZ = 0x1000;

  private static final Logger LOGGER = Logger.getLogger(IO.class.getName());
  
  private IO() {
    // Empty constructor prevents instantiation.
  }

  static String loadUtf8ResourceFromClasspath(String path) throws IOException {
    var resourceBytes = loadResourceFromClasspath(path);
    return new String(resourceBytes, Charset.forName("UTF-8"));
  }

  static byte[] loadResourceFromClasspath(String path) throws IOException {
    LOGGER.fine(() -> String.format("Loading %s...", path));
    byte[] contents = null;
    var resource = IO.class.getResourceAsStream(path);
    try {
      if (resource == null) {
        LOGGER.fine((() -> String.format(
          "%s not found! Returning empty byte array!", path)));
        return new byte[0];
      }
      contents = toByteArray(resource);
    } finally {
      if (resource != null) {
        resource.close();
      }
    }
    LOGGER.fine(() ->  String.format("%s loaded successfully!", path));
    return contents;
  }

  static byte[] toByteArray(InputStream in) throws IOException {
    var bos = new ByteArrayOutputStream();
    var buf = new byte[BUF_SZ];
    while (true) {
      var read = in.read(buf);
      if (read == -1) {
        break;
      }
      bos.write(buf, 0, read);
    }
    return bos.toByteArray();    
  }

  static long lastMTime(String path) throws IOException {
    var lastModifiedTime = -1l;
    LOGGER.finer(() -> path);
    try {
      if (IO.class.getResource(path) != null) {
        var fileUri = IO.class.getResource(path).toURI();
        LOGGER.finer(() -> "Reading last MTime on " + fileUri.toASCIIString());
        if (fileUri.toASCIIString().startsWith("jar:")) {
          var jarname = fileUri.toASCIIString().split("!")[0].substring("jar:file:".length());
          LOGGER.finest(() -> "jarfile name = " + jarname);
          try (var jar = new ZipFile(new File(jarname))) {
            var entryname = path.startsWith("/") ? path.substring(1) : path;
            lastModifiedTime = jar.getEntry(entryname).getTime();
          } catch (Exception e) {
            if (e instanceof RuntimeException) {
              throw (RuntimeException) e;
            } else {
              throw new RuntimeException(e);
            }
          }
        } else {
          lastModifiedTime = new File(fileUri).lastModified();
        }
      }
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    LOGGER.finer("Last Modified Time = " + lastModifiedTime);
    return lastModifiedTime;
  }
}
