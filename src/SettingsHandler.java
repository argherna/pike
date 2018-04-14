import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class SettingsHandler implements HttpHandler {

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();
    switch (method) {
      case "GET":
        doGet(exchange);
        break;
      default:
        Map<String, List<String>> responseHeaders = new HashMap<>();
        Http.addServerHeaders(responseHeaders, Pike.SERVER_STRING);
        Http.addContentTypeResponseHeaders(responseHeaders, 
          ContentTypes.TYPES.get("json"));
        responseHeaders.put("Allow", List.of("GET"));
        byte[] content = Json.renderError(
          String.format("Method %s not allowed!", method)).getBytes();
        Http.sendResponse(exchange, HttpStatus.METHOD_NOT_ALLOWED, content, 
          responseHeaders);
        return;
    }
  }

  private void doGet(HttpExchange exchange) throws IOException {
    Map<String, List<String>> responseHeaders = new HashMap<>();
    Http.addServerHeaders(responseHeaders, Pike.SERVER_STRING);
    String name = Http.getLastPathComponent(exchange.getRequestURI().getRawPath());
    String contextPath = exchange.getHttpContext().getPath().substring(1);
    String contentType = ContentTypes.TYPES.get("xml");
    HttpStatus status = HttpStatus.OK;
    byte[] content = new byte[0];
    String filename = "pike.prefs";
    try {
      if (name.equals(contextPath)){
        content = Settings.exportAllConnectionSettings();
      } else {
        content = Settings.exportConnectionSettings(name);
        filename = String.format("pike-%s.prefs", name);
      }
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
    
    responseHeaders.put("Content-Disposition", 
      List.of(String.format("attachment; filename=%s", filename)));
    Http.addContentTypeResponseHeaders(responseHeaders, 
      contentType);
    Http.sendResponse(exchange, status, content, responseHeaders);
  }
}