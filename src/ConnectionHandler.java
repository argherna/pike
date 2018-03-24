import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class ConnectionHandler implements HttpHandler {
  
  private static final Logger LOGGER = Logger.getLogger(
    ConnectionHandler.class.getName());

  private static final Function<String, List<String>> CHECKBOX_PROCESSOR =
    s -> {
      return Arrays.asList(Boolean.valueOf(s).toString());
    };
  
  private static final Map<String, Function<String, List<String>>> PARAM_PROCS;

  static {
    Map<String, Function<String, List<String>>> paramProcs = new HashMap<>();
    paramProcs.put("usestarttls", CHECKBOX_PROCESSOR);
    PARAM_PROCS = Collections.unmodifiableMap(paramProcs);
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    if (exchange.getRequestMethod().equals("GET") || 
      exchange.getRequestMethod().equals("HEAD")) {
      doGet(exchange);
    } else if (exchange.getRequestMethod().equals("DELETE")) {
      doDelete(exchange);
    } else {
      doPost(exchange);
    }
  }

  private void doGet(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath().replaceFirst(
      "/connection", "");
    byte[] content = null;
    String name = null;
    String ldapUrl = null;
    String baseDn = null;
    String bindDn = null;
    boolean useStartTls = false;
    String mode = "";
    if (!path.isEmpty()) {
      name = path.substring(1, path.length());
      Preferences settings = Settings.getConnectionSettings(name);
      try {
        if (settings.keys().length > 0) {
          ldapUrl = settings.get(Settings.LDAP_URL_SETTING, "");
          baseDn = settings.get(Settings.BASE_DN_SETTING, "");
          bindDn = settings.get(Settings.BIND_DN_SETTING, "");
          useStartTls = settings.getBoolean(
            Settings.USE_STARTTLS_SETTING, false);
        } 
        if (exchange.getRequestURI().getRawQuery() != null) {
          Map<String, List<String>> parameters = IO.queryToMap(
            exchange.getRequestURI().getRawQuery());
          if (parameters.containsKey("mode")) {
            mode = parameters.get("mode").get(0);
          }
        }
      } catch (BackingStoreException e) {
        throw new RuntimeException(e);
      } 
    } else {
      // Might be a new connection.
      mode = "edit";
    }
    content = Pages.renderConnection(name, ldapUrl, baseDn, bindDn, 
      useStartTls, mode).getBytes();
    HttpStatus status = HttpStatus.OK;
    String contentType = ContentTypes.TYPES.get("html");
    IO.sendResponse(exchange, status, content, contentType);
  }

  private void doPost(HttpExchange exchange) throws IOException {

    InputStream requestBodyStream = exchange.getRequestBody();
    Headers requestHeaders = exchange.getRequestHeaders();
    String contentType = requestHeaders.get(
      "Content-Type") != null ? requestHeaders.get("Content-Type").get(0) : "";
    Map<String, List<String>> connectionSettings = new HashMap<>();
    if (contentType.equals(ContentTypes.TYPES.get("form"))) {
      String formParameters = new String(IO.toByteArray(requestBodyStream),
        "UTF-8");
      connectionSettings.putAll(IO.queryToMap(formParameters, PARAM_PROCS));
    }

    String name = connectionSettings.get("name") != null ?
      connectionSettings.get("name").get(0) : "";
    String ldapUrl = connectionSettings.get("ldapurl") != null ?
      connectionSettings.get("ldapurl").get(0) : "";
    String baseDn = connectionSettings.get("basedn") != null ?
      connectionSettings.get("basedn").get(0) : "";
    String bindDn = connectionSettings.get("binddn") != null ?
      connectionSettings.get("binddn").get(0) : "";
    String password = connectionSettings.get("password") != null ?
      connectionSettings.get("password").get(0) : "";
    boolean useStartTls = connectionSettings.get("usestarttls") != null ?
      Boolean.valueOf(connectionSettings.get("usestarttls").get(0)) : false;

    try {
      Settings.saveConnectionSettings(name, ldapUrl, 
        baseDn, bindDn, password, useStartTls); 
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    LOGGER.fine(() -> {
      return String.format(
        "Settings for %1$s saved; sending to /connection/%1$s", name);
    });
    IO.sendResponseWithLocationNoContent(exchange, HttpStatus.FOUND, 
      ContentTypes.TYPES.get("html"), 
      "/connection/" + name);
  }

  private void doDelete(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    if (!path.endsWith(exchange.getHttpContext().getPath())) {
      String[] pathElements = path.split("/");
      String connectionName = pathElements[pathElements.length - 1];
      Preferences connectionSettings = Settings.getConnectionSettings(
        connectionName);
      try {
        connectionSettings.removeNode();
        connectionSettings.flush();
        LOGGER.info(
          String.format("Deleted connection settings for %s", connectionName));
      } catch (BackingStoreException e) {
        throw new RuntimeException(e);
      }
      IO.sendResponse(exchange, HttpStatus.NO_CONTENT, new byte[0],
        ContentTypes.TYPES.get("html"));
    }
  }
}