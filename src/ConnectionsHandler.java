import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class ConnectionsHandler implements HttpHandler {
  
  @Override
  public void handle(HttpExchange exchange) throws IOException {
    byte[] content = new byte[0];
    HttpStatus status = HttpStatus.OK;
    String contentType = ContentTypes.TYPES.get("html");

    Preferences connectionSettings = Settings.getAllConnectionSettings();
    String[] connectionNames = new String[0];
    try {
      connectionNames = connectionSettings.childrenNames();
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }

    if (connectionNames.length == 0) {
      status = HttpStatus.TEMPORARY_REDIRECT;
      Http.sendResponseWithLocationNoContent(exchange, status, contentType, 
        "/connection");
      return;
    }

    String path = exchange.getRequestURI().getRawPath();
    String handlerPath = exchange.getHttpContext().getPath();
    if (path.endsWith(handlerPath)) {
      content = IO.loadResourceFromClasspath("templates/connections.html");
    } else if (path.endsWith("settings")) {
      contentType = ContentTypes.TYPES.get("json");
      content = Json.renderConnections(connectionSettings).getBytes();
    }
    Http.sendResponse(exchange, status, content, contentType);
  }
}