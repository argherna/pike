import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class ConnectionHandler implements HttpHandler {

  private static final Logger LOGGER = Logger.getLogger(ConnectionHandler.class.getName());

  private static final Function<String, List<String>> CHECKBOX_PROCESSOR = s -> {
    boolean booleanValue = (s.equalsIgnoreCase("on") || s.equalsIgnoreCase("true"));
    return Arrays.asList(Boolean.valueOf(booleanValue).toString());
  };

  private static final Map<String, Function<String, List<String>>> PARAM_PROCS = Collections
      .unmodifiableMap(Collections.singletonMap("usestarttls", CHECKBOX_PROCESSOR));

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    if (exchange.getRequestMethod().equals("GET") || exchange.getRequestMethod().equals("HEAD")) {
      doGet(exchange);
    } else if (exchange.getRequestMethod().equals("DELETE")) {
      doDelete(exchange);
    } else if (exchange.getRequestMethod().equals("PATCH")) {
      doPatch(exchange);
    } else {
      doPost(exchange);
    }
  }

  private void doGet(HttpExchange exchange) throws IOException {
    Headers h = exchange.getRequestHeaders();
    if (h.containsKey("Accept")) {
      List<String> accept = h.get("Accept");
      if (accept.contains(ContentTypes.TYPES.get("json"))) {
        doGetJson(exchange);
      } else {
        doGetHtml(exchange);
      }
    } else {
      doGetHtml(exchange);
    }
  }

  private void doGetHtml(HttpExchange exchange) throws IOException {
    Http.sendResponse(exchange, HttpStatus.OK, IO.loadResourceFromClasspath("templates/connection.html"),
        ContentTypes.TYPES.get("html"));
  }

  private void doGetJson(HttpExchange exchange) throws IOException {
    String name = Http.getLastPathComponent(exchange.getRequestURI().getPath());
    byte[] content = null;
    String contentType = ContentTypes.TYPES.get("json");
    HttpStatus status = HttpStatus.OK;
    if (!name.isEmpty()) {
      Preferences connection = Settings.getConnectionSettings(name);
      try {
        content = Json.renderObject(Settings.getConnectionSettingsAsMap(name)).getBytes();
      } catch (Exception e) {
        if (e instanceof IOException) {
          throw (IOException) e;
        } else {
          throw new RuntimeException(e);
        }
      }
    } else {
      status = HttpStatus.BAD_REQUEST;
      content = Html.renderError(status, "Connection wasn't specified in Url path!").getBytes();
      contentType = ContentTypes.TYPES.get("html");
    }
    Http.sendResponse(exchange, status, content, contentType);
  }

  private void doPost(HttpExchange exchange) throws IOException {

    InputStream requestBodyStream = exchange.getRequestBody();
    Headers requestHeaders = exchange.getRequestHeaders();
    String contentType = requestHeaders.get("Content-Type") != null ? requestHeaders.get("Content-Type").get(0) : "";
    Map<String, List<String>> connectionSettings = new HashMap<>();
    if (contentType.equals(ContentTypes.TYPES.get("form"))) {
      String formParameters = new String(IO.toByteArray(requestBodyStream), "UTF-8");
      connectionSettings.putAll(Http.queryToMap(formParameters, PARAM_PROCS));
    }

    String name = connectionSettings.get("name") != null ? connectionSettings.get("name").get(0) : "";
    String ldapUrl = connectionSettings.get("ldapurl") != null ? connectionSettings.get("ldapurl").get(0) : "";
    String baseDn = connectionSettings.get("basedn") != null ? connectionSettings.get("basedn").get(0) : "";
    String authType = connectionSettings.get("authtype") != null ? connectionSettings.get("authtype").get(0) : "";
    String bindDn = connectionSettings.get("binddn") != null ? connectionSettings.get("binddn").get(0) : "";
    String password = connectionSettings.get("password") != null ? connectionSettings.get("password").get(0) : "";
    String referralPolicy = connectionSettings.get("referralpolicy") != null
        ? connectionSettings.get("referralpolicy").get(0)
        : "";

    boolean useStartTls = connectionSettings.get("usestarttls") != null
        ? Boolean.valueOf(connectionSettings.get("usestarttls").get(0))
        : false;

    try {
      String prefnode = String.format("%s/%s", Settings.CONNECTION_PREFS_ROOT_NODE_NAME, name);
      Preferences connectionPrefs = Preferences.userRoot().node(prefnode);
      connectionPrefs.put(Settings.LDAP_URL_SETTING, ldapUrl);
      connectionPrefs.put(Settings.BASE_DN_SETTING, baseDn);
      connectionPrefs.put(Settings.BIND_DN_SETTING, bindDn);
      if (!password.isEmpty()) {
        // Settings#secretToByteArray could throw some wacky security
        // exceptions.
        connectionPrefs.putByteArray(Settings.PASSWORD_SETTING,
            Settings.secretToByteArray(bindDn, password.getBytes()));
      }
      connectionPrefs.putBoolean(Settings.USE_STARTTLS_SETTING, useStartTls);
      connectionPrefs.put(Settings.AUTHTYPE_SETTING, authType);
      connectionPrefs.put(Settings.REFERRAL_POLICY_SETTING, referralPolicy);
      // These 2 could throw a BackingStoreException.
      connectionPrefs.flush();
      connectionPrefs.sync();
      LOGGER.fine(() -> String.format("Saved %s settings: %s=%s,%s=%s,%s=%s,%s=********,%s=%b,%s=%s,%s=%s", name,
          Settings.LDAP_URL_SETTING, ldapUrl, Settings.BASE_DN_SETTING, baseDn, Settings.BIND_DN_SETTING, bindDn,
          Settings.PASSWORD_SETTING, Settings.USE_STARTTLS_SETTING, useStartTls, Settings.AUTHTYPE_SETTING, authType,
          Settings.REFERRAL_POLICY_SETTING, referralPolicy));
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      }
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }
      throw new RuntimeException(e);
    }
    LOGGER.fine(() -> String.format("Settings for %1$s saved; sending to /connection/%1$s", name));
    Http.sendResponseWithLocationNoContent(exchange, HttpStatus.FOUND, ContentTypes.TYPES.get("html"),
        "/connection/" + name);
  }

  private void doPatch(HttpExchange exchange) throws IOException {
    String path = Http.getLastPathComponent(exchange.getRequestURI().getPath());
    if (path.isEmpty()) {
      HttpStatus status = HttpStatus.BAD_REQUEST;
      byte[] content = Html.renderError(status, "Connection name not specified!").getBytes();
      Http.sendResponse(exchange, status, content, ContentTypes.TYPES.get("html"));
      return;
    }

    try {
      Pike.activate(path);
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      } else {
        throw new RuntimeException(e);
      }
    }
    Http.sendResponse(exchange, HttpStatus.NO_CONTENT, new byte[0], ContentTypes.TYPES.get("html"));
  }

  private void doDelete(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    String contentType = ContentTypes.TYPES.get("html");
    if (!path.endsWith(exchange.getHttpContext().getPath())) {
      String connectionName = Http.getLastPathComponent(exchange.getRequestURI().getPath());
      try {
        Pike.delete(connectionName);
      } catch (Exception e) {
        if (e instanceof IOException) {
          throw (IOException) e;
        } else {
          throw new RuntimeException(e);
        }
      }
      Http.sendResponse(exchange, HttpStatus.NO_CONTENT, new byte[0], contentType);
    } else {
      HttpStatus status = HttpStatus.BAD_REQUEST;
      Http.sendResponse(exchange, status, Html.renderError(status, "Connection name not specified!").getBytes(),
          contentType);
    }
  }
}
