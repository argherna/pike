package com.github.argherna.pike;

import java.io.IOException;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

class FaviconFilter extends Filter {

  private static final String FAVICON_PATH = "/favicon.ico";

  @Override
  public String description() {
    return "Automatically return a 404 status for /favicon.ico requets";
  }

  @Override
  public void doFilter(HttpExchange exchange, Filter.Chain chain) 
    throws IOException {
    String path = exchange.getRequestURI().getPath();
    if (path.endsWith(FAVICON_PATH)) {
      HttpStatus status = HttpStatus.NOT_FOUND;
      byte[] content = Html.renderError(status, 
        "Server does not have a favicon").getBytes();
      String contentType = ContentTypes.TYPES.get("html");
      Http.sendResponse(exchange, status, content, contentType);
    } else {
      chain.doFilter(exchange);
    }
  }
}
