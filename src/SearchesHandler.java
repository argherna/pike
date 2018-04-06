import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class SearchesHandler implements HttpHandler {
  
  private static final Logger LOGGER = Logger.getLogger(
    SearchesHandler.class.getName());

  static final String RDN_SETTING = "rdn";

  static final String FILTER_SETTING = "filter";

  static final String ATTRS_TO_RETURN_SETTING = "attrs-to-return";

  static final String SCOPE_SETTING = "scope";

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();
    switch (method) {
      case "DELETE":
        doDelete(exchange);
        break;
      case "GET":
      case "HEAD":
        doGet(exchange);
        break;
      case "PATCH":
        doPatch(exchange);
        break;
      case "POST":
        doPost(exchange);
        break;
      default:
        Map<String, List<String>> responseHeaders = new HashMap<>();
        Http.addServerHeaders(responseHeaders, Pike.SERVER_STRING);
        Http.addContentTypeResponseHeaders(responseHeaders, 
          ContentTypes.TYPES.get("json"));
        responseHeaders.put("Allow", List.of("DELETE", "GET", "HEAD", "PATCH", 
          "POST"));
        byte[] content = Json.renderError(
          String.format("Method %s not allowed!", method)).getBytes();
        Http.sendResponse(exchange, HttpStatus.METHOD_NOT_ALLOWED, content, 
          responseHeaders);
        return;
    }
  }

  void doDelete(HttpExchange exchange) throws IOException {
    Map<String, Object> responseBody = new HashMap<>();
    Map<String, List<String>> responseHeaders = new HashMap<>();
    Http.addServerHeaders(responseHeaders, Pike.SERVER_STRING);
    String name = Http.getLastPathComponent(exchange.getRequestURI().getRawPath());
    String contextPath = exchange.getHttpContext().getPath().substring(1);
    byte[] content = new byte[0];
    HttpStatus status = HttpStatus.NO_CONTENT;
    if (name.equals(contextPath)) {
      Http.addContentTypeResponseHeaders(responseHeaders, 
        ContentTypes.TYPES.get("json"));
      status = HttpStatus.BAD_REQUEST;
      responseBody.put("error", "Saved search to delete must be in the path!");
      content = Json.renderObject(responseBody).getBytes();
    } else {
      String prefNodeName = preferenceNodeName(name);
      Preferences search = Preferences.userRoot().node(prefNodeName);
      if (getKeys(search).length == 0) {
        Http.addContentTypeResponseHeaders(responseHeaders, 
          ContentTypes.TYPES.get("json"));
        responseBody.put("error", String.format("%s search not found!", name));
        content = Json.renderObject(responseBody).getBytes();
        status = HttpStatus.NOT_FOUND;
      } else {
        try {
          search.removeNode();
          search.flush();
        } catch (BackingStoreException e) {
          throw new RuntimeException(e);
        }
        LOGGER.fine(String.format("Deleted saved search %s.", name));
      }
    }
    Http.sendResponse(exchange, status, content, responseHeaders);
  }
  
  void doGet(HttpExchange exchange) throws IOException {
    Map<String, Object> responseBody = new HashMap<>();
    Map<String, List<String>> responseHeaders = new HashMap<>();
    Http.addServerHeaders(responseHeaders, Pike.SERVER_STRING);
    Http.addContentTypeResponseHeaders(responseHeaders, 
      ContentTypes.TYPES.get("json"));
    String name = Http.getLastPathComponent(exchange.getRequestURI().getRawPath());
    String contextPath = exchange.getHttpContext().getPath().substring(1);
    byte[] content = new byte[0];
    HttpStatus status = HttpStatus.OK;
    if (name.equals(contextPath)) {
      Preferences searches = Preferences.userRoot().node(searchesNodeName());
      content = Json.renderListValues(
        Arrays.asList(getChildrenNames(searches))).getBytes();
    } else {
      String prefNodeName = preferenceNodeName(name);
      Preferences search = Preferences.userRoot().node(prefNodeName);
      if (getKeys(search).length == 0) {
        status = HttpStatus.NOT_FOUND;
        responseBody.put("error", String.format("%s search not found!", name));
        content = Json.renderObject(responseBody).getBytes();
      } else {
        responseBody.put("name", name);
        String rdn = search.get(RDN_SETTING, "");
        if (!Strings.isNullOrEmpty(rdn)) {
          responseBody.put(RDN_SETTING, rdn);
        }
        String filter = search.get(FILTER_SETTING, "");
        if (!Strings.isNullOrEmpty(filter)) {
          responseBody.put(FILTER_SETTING, filter);
        }
        String scope = search.get(SCOPE_SETTING, "");
        if (!Strings.isNullOrEmpty(scope)) {
          responseBody.put(SCOPE_SETTING, scope);
        }
        String attrsToReturn = search.get(ATTRS_TO_RETURN_SETTING, "");
        if (!Strings.isNullOrEmpty(attrsToReturn)) {
          List<String> attrs = Arrays.asList(attrsToReturn.split(","));
          responseBody.put("attrsToReturn", attrs);
        }
        content = Json.renderObject(responseBody).getBytes();
      }
    }
    Http.sendResponse(exchange, status, content, responseHeaders);
  }
  
  void doPatch(HttpExchange exchange) throws IOException {
    String name = Http.getLastPathComponent(exchange.getRequestURI().getRawPath());
    String contextPath = exchange.getHttpContext().getPath().substring(1);
    byte[] content = new byte[0];
    Map<String, List<String>> responseHeaders = new HashMap<>();
    Map<String, Object> responseBody = new HashMap<>();
    HttpStatus status = HttpStatus.NO_CONTENT;
    if (name.equals(contextPath)) {
      Http.addContentTypeResponseHeaders(responseHeaders, 
        ContentTypes.TYPES.get("json"));
      responseBody.put("error", "\"name\" for settings to update not given!");
      content = Json.renderObject(responseBody).getBytes();
      status = HttpStatus.BAD_REQUEST;
    } else {
      Map<String, Object> params = Json.marshal(
        new String(exchange.getRequestBody().readAllBytes()));
      updateSavedSearch(name, params);
    }

    Http.addServerHeaders(responseHeaders, Pike.SERVER_STRING);
    Http.sendResponse(exchange, status, content, responseHeaders);
  }
  
  void doPost(HttpExchange exchange) throws IOException {
    Map<String, Object> params = Json.marshal(
      new String(exchange.getRequestBody().readAllBytes()));
    byte[] content = new byte[0];
    HttpStatus status = HttpStatus.CREATED;
    Map<String, List<String>> responseHeaders = new HashMap<>();
    Map<String, Object> responseBody = new HashMap<>();
    if (!params.containsKey("name")) {
      Http.addContentTypeResponseHeaders(responseHeaders, 
        ContentTypes.TYPES.get("json"));
      responseBody.put("error", "\"name\" for settings not given!");
      content = Json.renderObject(responseBody).getBytes();
      status = HttpStatus.BAD_REQUEST;
    } else {
      String name = params.get("name").toString();
      updateSavedSearch(name, params);
      responseHeaders.put("Location", List.of(String.format("/%s/%s", 
        exchange.getHttpContext().getPath(), name)));
    }

    Http.addServerHeaders(responseHeaders, Pike.SERVER_STRING);
    Http.sendResponse(exchange, status, content, responseHeaders);
  }

  private void updateSavedSearch(String name, Map<String, Object> params) {
    String prefNodeName = preferenceNodeName(name);
    Preferences search = Preferences.userRoot().node(prefNodeName);
    
    String rdn = null;
    if (params.containsKey("rdn")) {
      rdn = params.get("rdn").toString();
      search.put(RDN_SETTING, rdn);
    }
    String filter = null;
    if (params.containsKey("filter")) {
      filter = params.get("filter").toString();
      search.put(FILTER_SETTING, filter);
    }
    String attrsToReturn = null;
    if (params.containsKey("attrsToReturn")) {
      // If I can't trust myself, who the hell can I trust???
      @SuppressWarnings("unchecked")
      List<String> attrs = (List<String>) params.get("attrsToReturn");
      StringJoiner sj = new StringJoiner(",");
      attrs.stream().forEach(attr -> sj.add(attr));
      attrsToReturn = sj.toString();
      search.put(ATTRS_TO_RETURN_SETTING, attrsToReturn);
    }
    String scope = null;
    if (params.containsKey("scope")) {
      scope = params.get("scope").toString();
      search.put(SCOPE_SETTING, scope);
    }
    try {
      search.flush();
      search.sync();
      LOGGER.fine(String.format("Saved search %s: %s=%s, %s=%s, %s=%s, %s=%s",
        prefNodeName, RDN_SETTING, rdn, FILTER_SETTING, filter, 
        ATTRS_TO_RETURN_SETTING, attrsToReturn, SCOPE_SETTING, scope));
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  private String searchesNodeName() {
    String searchesNodeName = String.format("%s/%s/searches",
      Settings.CONNECTION_PREFS_ROOT_NODE_NAME, 
      Settings.getActiveConnectionName());
    LOGGER.fine(() -> String.format("searchesNodeName = %s", 
      searchesNodeName));
    return searchesNodeName;
  }

  private String preferenceNodeName(String searchName) {
    String preferenceNodeName = String.format("%s/%s", searchesNodeName(),
      searchName);
    LOGGER.fine(() -> String.format("preferenceNodeName = %s",
      preferenceNodeName));
    return preferenceNodeName;
  }

  private String[] getKeys(Preferences prefs) {
    String[] keys = new String[0];
    try {
      keys = prefs.keys();
    } catch (BackingStoreException e) {
      LOGGER.log(Level.FINE, 
        String.format("%s keys not found, returning empty array",
          prefs.name()), e);
      keys = new String[0];
    }
    return keys;
  }

  private String[] getChildrenNames(Preferences prefs) {
    String[] chlidrenNames = new String[0];
    try {
      chlidrenNames = prefs.childrenNames();
    } catch (BackingStoreException e) {
      LOGGER.log(Level.FINE, 
        String.format("%s children not found, returning empty array",
          prefs.name()), e);
      chlidrenNames = new String[0];
    }
    return chlidrenNames;
  }
}