package com.github.argherna.pike;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

class JsonInFilter extends Filter {

  private static final List<String> UPDATE_METHODS = List.<String>of("PATCH", "POST", "PUT");

  @Override
  public String description() {
    return "Asserts incoming requests targeted for Json write methods" + " have a Content-Type of 'application/json'.";
  }

  @Override
  public void doFilter(HttpExchange exchange, Filter.Chain chain) throws IOException {
    var method = exchange.getRequestMethod();
    if (UPDATE_METHODS.contains(method)) {
      var rhdrs = exchange.getRequestHeaders();
      if (!rhdrs.containsKey("Content-Type")
          || !rhdrs.getFirst("Content-Type").equals(ContentTypes.TYPES.get("json"))) {

        var responseBody = new HashMap<String, Object>();
        responseBody.put("error", "Content-Type not set or is not " + ContentTypes.TYPES.get("json"));

        var responseHeaders = new HashMap<String, List<String>>();
        Http.addContentTypeResponseHeaders(responseHeaders, ContentTypes.TYPES.get("json"));
        Http.sendResponse(exchange, HttpStatus.BAD_REQUEST, Json.renderObject(responseBody).getBytes(),
            ContentTypes.TYPES.get("json"));
      } else {
        chain.doFilter(exchange);
      }
    } else {
      chain.doFilter(exchange);
    }
  }
}
