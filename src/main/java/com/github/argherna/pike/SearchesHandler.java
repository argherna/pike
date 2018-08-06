package com.github.argherna.pike;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class SearchesHandler implements HttpHandler {

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    var method = exchange.getRequestMethod();
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
      var responseHeaders = new HashMap<String, List<String>>();
      Http.addContentTypeResponseHeaders(responseHeaders, ContentTypes.TYPES.get("json"));
      responseHeaders.put("Allow", List.of("DELETE", "GET", "HEAD", "PATCH", "POST"));
      var content = Json.renderObject(Map.of("error", String.format("Method %s not allowed!", method))).getBytes();
      Http.sendResponse(exchange, HttpStatus.METHOD_NOT_ALLOWED, content, responseHeaders);
      return;
    }
  }

  void doDelete(HttpExchange exchange) throws IOException {
    var responseHeaders = new HashMap<String, List<String>>();
    var name = Http.getLastPathComponent(exchange.getRequestURI().getRawPath());
    var content = new byte[0];
    var status = HttpStatus.NO_CONTENT;
    if (name.equals(exchange.getHttpContext().getPath().substring(1))) {
      Http.addContentTypeResponseHeaders(responseHeaders, ContentTypes.TYPES.get("json"));
      status = HttpStatus.BAD_REQUEST;
      content = Json.renderObject(Map.of("error", "Saved search to delete must be in the path!")).getBytes();
    } else {
      if (Settings.savedSearchExists(Settings.getActiveConnectionName(), name)) {
        Settings.deleteSingleSearch(Settings.getActiveConnectionName(), name);
      } else {
        Http.addContentTypeResponseHeaders(responseHeaders, ContentTypes.TYPES.get("json"));
        content = Json.renderObject(Map.of("error", String.format("%s search not found!", name))).getBytes();
        status = HttpStatus.NOT_FOUND;
      }
    }
    Http.sendResponse(exchange, status, content, responseHeaders);
  }

  void doGet(HttpExchange exchange) throws IOException {
    var responseHeaders = new HashMap<String, List<String>>();
    Http.addContentTypeResponseHeaders(responseHeaders, ContentTypes.TYPES.get("json"));
    var name = Http.getLastPathComponent(exchange.getRequestURI().getRawPath());
    var content = new byte[0];
    var status = HttpStatus.OK;
    if (name.equals(exchange.getHttpContext().getPath().substring(1))) {
      content = Json.renderList(Arrays.asList(Settings.getSearchNames(Settings.getActiveConnectionName()))).getBytes();
    } else {
      if (Settings.savedSearchExists(Settings.getActiveConnectionName(), name)) {
        content = Json.renderObject(Maps.toMap(Settings.getSearchSettings(Settings.getActiveConnectionName(), name)))
            .getBytes();
      } else {
        status = HttpStatus.NOT_FOUND;
        content = Json.renderObject(Map.of("error", String.format("%s search not found!", name))).getBytes();
      }
    }
    Http.sendResponse(exchange, status, content, responseHeaders);
  }

  void doPatch(HttpExchange exchange) throws IOException {
    var name = Http.getLastPathComponent(exchange.getRequestURI().getRawPath());
    var content = new byte[0];
    var responseHeaders = new HashMap<String, List<String>>();
    var status = HttpStatus.NO_CONTENT;
    if (name.equals(exchange.getHttpContext().getPath().substring(1))) {
      Http.addContentTypeResponseHeaders(responseHeaders, ContentTypes.TYPES.get("json"));
      content = Json.renderObject(Map.of("error", "\"name\" for settings to update not given!")).getBytes();
      status = HttpStatus.BAD_REQUEST;
    } else {
      Map<String, Object> params = Json.marshal(new String(exchange.getRequestBody().readAllBytes()));
      updateSavedSearch(name, params);
    }

    Http.sendResponse(exchange, status, content, responseHeaders);
  }

  void doPost(HttpExchange exchange) throws IOException {
    var params = Json.marshal(new String(exchange.getRequestBody().readAllBytes()));
    var content = new byte[0];
    var status = HttpStatus.CREATED;
    var responseHeaders = new HashMap<String, List<String>>();
    if (!params.containsKey("name")) {
      Http.addContentTypeResponseHeaders(responseHeaders, ContentTypes.TYPES.get("json"));
      content = Json.renderObject(Map.of("error", "\"name\" for settings not given!")).getBytes();
      status = HttpStatus.BAD_REQUEST;
    } else {
      var name = params.get("name").toString();
      updateSavedSearch(name, params);
      responseHeaders.put("Location", List.of(String.format("/%s/%s", exchange.getHttpContext().getPath(), name)));
    }

    Http.sendResponse(exchange, status, content, responseHeaders);
  }

  private void updateSavedSearch(String name, Map<String, Object> params) {
    Settings.SearchSettings.Builder searchSettings = new Settings.SearchSettings.Builder(name);

    if (params.containsKey("rdn")) {
      searchSettings.rdn(params.get("rdn").toString());
    }
    if (params.containsKey("filter")) {
      searchSettings.filter(params.get("filter").toString());
    }
    if (params.containsKey("attrsToReturn")) {
      // If I can't trust myself, who the hell can I trust???
      @SuppressWarnings("unchecked")
      var attrs = (List<String>) params.get("attrsToReturn");
      searchSettings.attrsToReturn(attrs);
    }
    if (params.containsKey("scope")) {
      searchSettings.scope(params.get("scope").toString());
    }
    try {
      Settings.saveSearchSettings(Settings.getActiveConnectionName(), searchSettings.build());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
