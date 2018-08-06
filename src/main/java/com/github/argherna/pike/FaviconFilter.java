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
  public void doFilter(HttpExchange exchange, Filter.Chain chain) throws IOException {
    var path = exchange.getRequestURI().getPath();
    if (path.endsWith(FAVICON_PATH)) {
      var status = HttpStatus.NOT_FOUND;
      var content = Html.renderError(status, "Server does not have a favicon").getBytes();
      Http.sendResponse(exchange, status, content, ContentTypes.TYPES.get("html"));
    } else {
      chain.doFilter(exchange);
    }
  }
}
