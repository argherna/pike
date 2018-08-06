package com.github.argherna.pike;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class ErrorHandler implements HttpHandler {

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    Http.sendResponse(exchange, HttpStatus.OK, IO.loadResourceFromClasspath("/templates/error.html"),
        ContentTypes.TYPES.get("html"));
  }
}
