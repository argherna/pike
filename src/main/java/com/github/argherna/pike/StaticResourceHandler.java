package com.github.argherna.pike;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class StaticResourceHandler implements HttpHandler {

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    var status = HttpStatus.OK;
    var path = exchange.getRequestURI().getPath();
    var contentType = ContentTypes.TYPES.get(getFileExtension(path));
    var content = IO.loadResourceFromClasspath(path);
    if (content.length == 0) {
      status = HttpStatus.NOT_FOUND;
      content = Html.renderError(status, String.format(
        "%s not found on this server", path)).getBytes();
      contentType = ContentTypes.TYPES.get("html");
    }
    Http.sendResponse(exchange, status, content, contentType);
  }

  private String getFileExtension(String filename) {
    var lastDot = filename.lastIndexOf('.');
    return lastDot > 0 ? filename.substring(lastDot + 1) : "";
  }
}
