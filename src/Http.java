import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

final class Http {

  private static final Logger LOGGER = Logger.getLogger(Http.class.getName());

  private Http() {
    // Empty constructor prevents instantiation.
  }

  static void sendResponse(HttpExchange exchange, HttpStatus status, 
    byte[] content, String contentType) throws IOException {
    Headers h = exchange.getResponseHeaders();
    h.add("Content-Type", contentType);
    h.add("Server", Pike.SERVER_STRING);
    if (contentType.equals(ContentTypes.TYPES.get("json"))) {
      h.add("Access-Control-Allow-Origin", "*");
      h.add("Access-Control-Allow-Headers", "origin, content-type, accept");
    }

    // Avoid NPE when writing response by setting content length to -1 when
    // request method is HEAD or byte array is 0-length.
    int length = 
      exchange.getRequestMethod().equals("HEAD") || content.length == 0 ||
        status == HttpStatus.NO_CONTENT ? -1 : content.length;
    exchange.sendResponseHeaders(status.getStatusCode(), length);

    if (content.length > 0) {
      OutputStream out = exchange.getResponseBody();
      out.write(content);
      out.flush();
      out.close();
    }

    exchange.close();    
  }

  static void sendResponseWithLocationNoContent(HttpExchange exchange, 
    HttpStatus status, String contentType, String location) 
    throws IOException {
    Headers h = exchange.getResponseHeaders();
    h.add("Content-Type", contentType);
    h.add("Server", Pike.SERVER_STRING);
    h.add("Location", location);
    LOGGER.fine(() -> {
      StringBuilder headers = new StringBuilder("{");
      for (String header : h.keySet()) {
        headers.append(header).append(": ").append(h.get(header).toString())
          .append(",");
      }
      headers.append("}");
      return String.format("Response headers %s", headers.toString());
    });
    exchange.sendResponseHeaders(status.getStatusCode(), -1);
    exchange.close();
  }

  static Map<String, List<String>> queryToMap(String rawQuery) {
    return queryToMap(rawQuery, new HashMap<>());
  }

  static Map<String, List<String>> queryToMap(String rawQuery, 
    Map<String, Function<String, List<String>>> parameterProcessors) {
    Map<String, List<String>> decodedParameters = new HashMap<>();
    if (!Strings.isNullOrEmpty(rawQuery)) {
      String[] parameters = rawQuery.split("&");
      for (String parameter : parameters) {
        String[] param = parameter.split("=");
        try {
          List<String> value = decodedParameters.get(param[0]);
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
    String[] pathComponents = uriPath.split("/");
    return pathComponents[pathComponents.length - 1];
  }
}