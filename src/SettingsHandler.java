import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class SettingsHandler implements HttpHandler {

  private static final String BOUNDARY = ContentTypes.TYPES.get("upload") + 
    "; boundary=";

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();
    switch (method) {
      case "GET":
        doGet(exchange);
        break;
      case "POST":
        doPost(exchange);
        break;
      default:
        Map<String, List<String>> responseHeaders = new HashMap<>();
        Http.addServerHeaders(responseHeaders, Pike.SERVER_STRING);
        Http.addContentTypeResponseHeaders(responseHeaders, 
          ContentTypes.TYPES.get("json"));
        responseHeaders.put("Allow", List.of("GET", "POST"));
        byte[] content = Json.renderObject(Map.of("error", String.format("Method %s not allowed!", method))).getBytes();
        Http.sendResponse(exchange, HttpStatus.METHOD_NOT_ALLOWED, content, 
          responseHeaders);
        return;
    }
  }

  private void doGet(HttpExchange exchange) throws IOException {
    Map<String, List<String>> responseHeaders = new HashMap<>();
    Http.addServerHeaders(responseHeaders, Pike.SERVER_STRING);
    String name = Http.getLastPathComponent(exchange.getRequestURI().getRawPath());
    String contextPath = exchange.getHttpContext().getPath().substring(1);
    String contentType = ContentTypes.TYPES.get("xml");
    HttpStatus status = HttpStatus.OK;
    byte[] content = new byte[0];
    String filename = "pike.prefs";
    try {
      if (name.equals(contextPath)){
        content = Settings.exportAllConnectionSettings();
      } else {
        content = Settings.exportConnectionSettings(name);
        filename = String.format("pike-%s.prefs", name);
      }
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
    
    responseHeaders.put("Content-Disposition", 
      List.of(String.format("attachment; filename=%s", filename)));
    Http.addContentTypeResponseHeaders(responseHeaders, 
      contentType);
    Http.sendResponse(exchange, status, content, responseHeaders);
  }

  private void doPost(HttpExchange exchange) throws IOException {
    Headers h = exchange.getRequestHeaders();
    String contentType = h.getFirst("Content-Type");
    HttpStatus status = HttpStatus.NO_CONTENT;
    byte[] content = new byte[0];
    if (contentType.startsWith(ContentTypes.TYPES.get("upload"))) {
      byte[] uploadedData = getUploadedData(exchange, contentType);
      try {
        Settings.importSettings(uploadedData);
      } catch (InvalidPreferencesFormatException e) {
        throw new RuntimeException(e);
      }
    } else {
      status = HttpStatus.BAD_REQUEST;
    }
    Http.sendResponse(exchange, status, content, 
      Collections.<String, List<String>>emptyMap());
  }

  private byte[] getUploadedData(HttpExchange exchange, String contentType) 
  throws IOException {
    String boundary = contentType.substring(BOUNDARY.length());
    String[] requestBodyLines = new String(exchange.getRequestBody()
      .readAllBytes()).split("\\n");
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    for (String line : requestBodyLines) {
      // Don't process the multipart boundary, any Content lines, or 
      // processing instructions. Simple for now, will be upgraded when needed.
      if (!line.contains(boundary) && 
        !line.startsWith("Content") && 
        !line.isEmpty() && !line.startsWith("<?")) {
        bos.write(line.getBytes());
      }
    }
    return bos.toByteArray();
  }
}
