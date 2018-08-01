package com.github.argherna.pike;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class StaticResourceHandler implements HttpHandler {

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    HttpStatus status = HttpStatus.OK;
    String path = exchange.getRequestURI().getPath();
    String contentType = ContentTypes.TYPES.get(getFileExtension(path));
    byte[] content = IO.loadResourceFromClasspath(path);
    if (content.length == 0) {
      status = HttpStatus.NOT_FOUND;
      content = Html.renderError(status, String.format(
        "%s not found on this server", path)).getBytes();
      contentType = ContentTypes.TYPES.get("html");
    }
    Http.sendResponse(exchange, status, content, contentType);
  }

  private String getFileExtension(String filename) {
    int lastDot = filename.lastIndexOf('.');
    return lastDot > 0 ? filename.substring(lastDot + 1) : "";
  }
}
