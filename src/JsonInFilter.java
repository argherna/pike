import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

class JsonInFilter extends Filter {
  
  private static final List<String> UPDATE_METHODS = 
    List.<String>of("PATCH", "POST", "PUT");
  
  @Override
  public String description() {
    return "Asserts incoming requests targeted for Json write methods" 
      + " have a Content-Type of 'application/json'.";
  }

  @Override
  public void doFilter(HttpExchange exchange, Filter.Chain chain) 
    throws IOException {
    String method = exchange.getRequestMethod();
    if (UPDATE_METHODS.contains(method)) {
      Headers rhdrs = exchange.getRequestHeaders();
      if (!rhdrs.containsKey("Content-Type") ||
        !rhdrs.getFirst("Content-Type").equals(ContentTypes.TYPES.get("json"))) {
  
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("error", "Content-Type not set or is not " +
          ContentTypes.TYPES.get("json")); 
        byte[] content = Json.renderObject(responseBody).getBytes();
  
        Map<String, List<String>> responseHeaders = new HashMap<>();
        Http.addContentTypeResponseHeaders(responseHeaders, 
          ContentTypes.TYPES.get("json"));
        Http.addServerHeaders(responseHeaders, Pike.SERVER_STRING);
        Http.sendResponse(exchange, HttpStatus.BAD_REQUEST, content, 
          ContentTypes.TYPES.get("json"));
      } else {
        chain.doFilter(exchange);
      }
    } else {
      chain.doFilter(exchange);
    }
  }
}