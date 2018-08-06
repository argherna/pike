package com.github.argherna.pike;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

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
    var h = exchange.getRequestHeaders();
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
    Http.sendResponse(exchange, HttpStatus.OK, IO.loadResourceFromClasspath("/templates/connection.html"),
        ContentTypes.TYPES.get("html"));
  }

  private void doGetJson(HttpExchange exchange) throws IOException {
    var name = Http.getLastPathComponent(exchange.getRequestURI().getPath());
    byte[] content = null;
    var contentType = ContentTypes.TYPES.get("json");
    var status = HttpStatus.OK;
    if (!name.isEmpty()) {
      try {
        content = Json.renderObject(Maps.toMap(Settings.getConnectionSettings(name))).getBytes();
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
    var requestBodyStream = exchange.getRequestBody();
    var requestHeaders = exchange.getRequestHeaders();
    var contentType = requestHeaders.get("Content-Type") != null ? requestHeaders.get("Content-Type").get(0) : "";
    var connectionSettings = new HashMap<String, List<String>>();
    if (contentType.equals(ContentTypes.TYPES.get("form"))) {
      connectionSettings.putAll(Http.queryToMap(new String(IO.toByteArray(requestBodyStream), "UTF-8"), PARAM_PROCS));
    }

    var name = connectionSettings.get("name") != null ? connectionSettings.get("name").get(0) : "";
    var ldapUrl = connectionSettings.get("ldapurl") != null ? connectionSettings.get("ldapurl").get(0) : "";
    var baseDn = connectionSettings.get("basedn") != null ? connectionSettings.get("basedn").get(0) : "";
    var authType = connectionSettings.get("authtype") != null ? connectionSettings.get("authtype").get(0) : "";
    var bindDn = connectionSettings.get("binddn") != null ? connectionSettings.get("binddn").get(0) : "";
    var password = connectionSettings.get("password") != null ? connectionSettings.get("password").get(0) : "";
    var referralPolicy = connectionSettings.get("referralpolicy") != null
        ? connectionSettings.get("referralpolicy").get(0)
        : "";

    var useStartTls = connectionSettings.get("usestarttls") != null
        ? Boolean.valueOf(connectionSettings.get("usestarttls").get(0))
        : false;

    try {
      Settings.saveConnectionSettings(new Settings.ConnectionSettings.Builder(name).ldapUrl(ldapUrl).baseDn(baseDn)
          .authType(authType).bindDn(bindDn).password(Settings.secretToByteArray(bindDn, password.getBytes()))
          .referralPolicy(referralPolicy).useStartTls(useStartTls).build());
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      }
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }
      throw new RuntimeException(e);
    }
    LOGGER.fine(() -> String.format("Redirecting to /connection/%1$s", name));
    Http.sendResponseWithLocationNoContent(exchange, HttpStatus.FOUND, ContentTypes.TYPES.get("html"),
        "/connection/" + name);
  }

  private void doPatch(HttpExchange exchange) throws IOException {
    var path = Http.getLastPathComponent(exchange.getRequestURI().getPath());
    if (path.isEmpty()) {
      var status = HttpStatus.BAD_REQUEST;
      Http.sendResponse(exchange, status, Html.renderError(status, "Connection name not specified!").getBytes(),
          ContentTypes.TYPES.get("html"));
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
    var path = exchange.getRequestURI().getPath();
    var contentType = ContentTypes.TYPES.get("html");
    if (!path.endsWith(exchange.getHttpContext().getPath())) {
      var connectionName = Http.getLastPathComponent(exchange.getRequestURI().getPath());
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
      var status = HttpStatus.BAD_REQUEST;
      Http.sendResponse(exchange, status, Html.renderError(status, "Connection name not specified!").getBytes(),
          contentType);
    }
  }
}
