package com.github.argherna.pike;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class SettingsHandler implements HttpHandler {

  private static final String BOUNDARY = ContentTypes.TYPES.get("upload") + "; boundary=";

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    var method = exchange.getRequestMethod();
    switch (method) {
    case "GET":
      doGet(exchange);
      break;
    case "POST":
      doPost(exchange);
      break;
    default:
      var responseHeaders = new HashMap<String, List<String>>();
      Http.addContentTypeResponseHeaders(responseHeaders, ContentTypes.TYPES.get("json"));
      responseHeaders.put("Allow", List.of("GET", "POST"));
      var content = Json.renderObject(Map.of("error", String.format("Method %s not allowed!", method))).getBytes();
      Http.sendResponse(exchange, HttpStatus.METHOD_NOT_ALLOWED, content, responseHeaders);
      return;
    }
  }

  private void doGet(HttpExchange exchange) throws IOException {
    var responseHeaders = new HashMap<String, List<String>>();
    var name = Http.getLastPathComponent(exchange.getRequestURI().getRawPath());
    var content = new byte[0];
    var filename = "pike.prefs";
    if (name.equals(exchange.getHttpContext().getPath().substring(1))) {
      content = Settings.exportAllConnectionSettings();
    } else {
      content = Settings.exportConnectionSettings(name);
      filename = String.format("pike-%s.prefs", name);
    }

    responseHeaders.put("Content-Disposition", List.of(String.format("attachment; filename=%s", filename)));
    Http.addContentTypeResponseHeaders(responseHeaders, ContentTypes.TYPES.get("xml"));
    Http.sendResponse(exchange, HttpStatus.OK, content, responseHeaders);
  }

  private void doPost(HttpExchange exchange) throws IOException {
    var h = exchange.getRequestHeaders();
    var contentType = h.getFirst("Content-Type");
    var status = HttpStatus.NO_CONTENT;
    if (contentType.startsWith(ContentTypes.TYPES.get("upload"))) {
      Settings.importSettings(getUploadedData(exchange, contentType));
    } else {
      status = HttpStatus.BAD_REQUEST;
    }
    Http.sendResponse(exchange, status, new byte[0], Collections.<String, List<String>>emptyMap());
  }

  private byte[] getUploadedData(HttpExchange exchange, String contentType) throws IOException {
    var boundary = contentType.substring(BOUNDARY.length());
    var requestBodyLines = new String(exchange.getRequestBody().readAllBytes()).split("\\n");
    var bos = new ByteArrayOutputStream();
    for (String line : requestBodyLines) {
      // Don't process the multipart boundary, any Content lines, or
      // processing instructions. Simple for now, will be upgraded when needed.
      if (!line.contains(boundary) && !line.startsWith("Content") && !line.isEmpty() && !line.startsWith("<?")) {
        bos.write(line.getBytes());
      }
    }
    return bos.toByteArray();
  }
}
