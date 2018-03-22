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
      IO.sendResponseWithLocationNoContent(exchange, status, contentType, 
        "/connection");
      return;
    }

    List<String> ldapHosts = new ArrayList<>();
    for (String connectionName : connectionNames) {
      Preferences connSettings = 
        Settings.getConnectionSettings(connectionName);
      String ldapUrl = connSettings.get(Settings.LDAP_URL_SETTING, "not-set");
      if (!ldapUrl.equals("not-set")) {
        ldapHosts.add(URI.create(ldapUrl).getHost());
      } 
    }
    List<StringTuple> connectionData = Strings.zip(
      Arrays.asList(connectionNames), ldapHosts);

    content = Pages.renderConnections(connectionData).getBytes();
    IO.sendResponse(exchange, status, content, contentType);
  }
}