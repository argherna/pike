package com.github.argherna.pike;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class ErrorHandler implements HttpHandler {

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    HttpStatus status = HttpStatus.OK;
    byte[] content = IO.loadResourceFromClasspath("/templates/error.html");
    Http.sendResponse(exchange, status, content, ContentTypes.TYPES.get("html"));
  }
}
