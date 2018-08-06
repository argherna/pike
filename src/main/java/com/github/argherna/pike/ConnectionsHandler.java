package com.github.argherna.pike;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class ConnectionsHandler implements HttpHandler {

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    byte[] content = new byte[0];
    HttpStatus status = HttpStatus.OK;
    String contentType = ContentTypes.TYPES.get("html");

    String[] connectionNames = Settings.getAllConnectionNames();
    if (connectionNames.length == 0) {
      status = HttpStatus.TEMPORARY_REDIRECT;
      Http.sendResponseWithLocationNoContent(exchange, status, contentType, "/connection");
      return;
    }

    String path = exchange.getRequestURI().getRawPath();
    String handlerPath = exchange.getHttpContext().getPath();
    if (path.endsWith(handlerPath)) {
      content = IO.loadResourceFromClasspath("/templates/connections.html");
    } else if (path.endsWith("settings")) {
      contentType = ContentTypes.TYPES.get("json");
      content = Json.renderList(Arrays.stream(connectionNames).map(n -> Maps.toMap(Settings.getConnectionSettings(n)))
          .collect(Collectors.toList())).getBytes();
    }
    Http.sendResponse(exchange, status, content, contentType);
  }
}
