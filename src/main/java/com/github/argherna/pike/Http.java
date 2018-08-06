package com.github.argherna.pike;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;

final class Http {

  private Http() {
    // Empty constructor prevents instantiation.
  }

  static void sendResponseWithLocationNoContent(HttpExchange exchange, 
    HttpStatus status, String contentType, String location) 
    throws IOException {
    var responseHeaders = new HashMap<String, List<String>>();
    addContentTypeResponseHeaders(responseHeaders, contentType);
    responseHeaders.put("Location", List.of(location));
    sendResponse(exchange, status, new byte[0], responseHeaders);
  }

  static void sendResponse(HttpExchange exchange, HttpStatus status, 
    byte[] content, String contentType) throws IOException {
    var responseHeaders = new HashMap<String, List<String>>();
    addContentTypeResponseHeaders(responseHeaders, contentType);
    sendResponse(exchange, status, content, responseHeaders);
  }

  static void sendResponse(HttpExchange exchange, HttpStatus status, 
    byte[] content, Map<String, List<String>> responseHeaders) 
    throws IOException {
    var h = exchange.getResponseHeaders();
    if (responseHeaders != null) {
      for (String headerName : responseHeaders.keySet()) {
        var values = responseHeaders.get(headerName);
        for (String value : values) {
          h.add(headerName, value);
        }
      }
    }

    // Avoid NPE when writing response by setting content length to -1 when
    // request method is HEAD or byte array is 0-length.
    var length = 
      exchange.getRequestMethod().equals("HEAD") || content.length == 0 ||
        status == HttpStatus.NO_CONTENT ? -1 : content.length;
    exchange.sendResponseHeaders(status.getStatusCode(), length);

    if (content.length > 0) {
      try (var out = exchange.getResponseBody()) {
        out.write(content);
        out.flush();
      } 
    }

    exchange.close();    
  }

  static void addContentTypeResponseHeaders(
    Map<String, List<String>> responseHeaders, String contentType) {
    responseHeaders.put("Content-Type", List.of(contentType));

    // CORS headers (see https://www.w3.org/TR/cors/)
    if (contentType.equals(ContentTypes.TYPES.get("json"))) {
      responseHeaders.put("Access-Control-Allow-Origin", List.of("*"));
      responseHeaders.put("Access-Control-Allow-Headers", 
        List.of("origin, content-type, accept"));
    }
  }

  static Map<String, List<String>> queryToMap(String rawQuery) {
    return queryToMap(rawQuery, new HashMap<>());
  }

  static Map<String, List<String>> queryToMap(String rawQuery, 
    Map<String, Function<String, List<String>>> parameterProcessors) {
    var decodedParameters = new HashMap<String, List<String>>();
    if (!Strings.isNullOrEmpty(rawQuery)) {
      var parameters = rawQuery.split("&");
      for (String parameter : parameters) {
        var param = parameter.split("=");
        try {
          var value = decodedParameters.get(param[0]);
          if (value == null) {
            value = new ArrayList<>();
          }
          if (param.length > 1 && !Strings.isNullOrEmpty(param[1])) {
            if (parameterProcessors.containsKey(param[0])) {
              value.addAll(parameterProcessors.get(param[0]).apply(param[1]));
            } else {
              value.add(URLDecoder.decode(param[1], "UTF-8"));
            }
            decodedParameters.put(param[0], value);
          }
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return decodedParameters;
  }

  static String getLastPathComponent(String uriPath) {
    var pathComponents = uriPath.split("/");
    return pathComponents[pathComponents.length - 1];
  }
}
