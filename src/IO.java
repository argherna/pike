import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.naming.directory.SearchControls;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

final class IO {

  private static int BUF_SZ = 0x1000;

  private static final Logger LOGGER = Logger.getLogger(IO.class.getName());
  
  private IO (){
    // Empty constructor prevents instantiation.
  }
  
  static void sendResponse(HttpExchange exchange, HttpStatus status, 
    byte[] content, String contentType) throws IOException {
    Headers h = exchange.getResponseHeaders();
    h.add("Content-Type", contentType);
    h.add("Server", Server.SERVER_STRING);
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
      h.add("Server", Server.SERVER_STRING);
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

  static String getFilter(Map<String, List<String>> parameters) {
    return parameters.containsKey("filter") ? parameters.get("filter").get(0) : 
      "(objectClass=*)";
  }

  static int getSearchScope(Map<String, List<String>> parameters) {
    // Do a subtree search by default. If another (valid) scope is specified 
    // then search with that.
    int scope = SearchControls.SUBTREE_SCOPE;
    if (parameters.containsKey("scope")) {
      String value = parameters.get("scope").get(0);
      if (value.equalsIgnoreCase("object")) {
        scope = SearchControls.OBJECT_SCOPE;
      } else if (value.equalsIgnoreCase("onelevel")) {
        scope = SearchControls.ONELEVEL_SCOPE;
      }
    }
    return scope;
  }

  static String[] getReturnAttributes(Map<String, List<String>> parameters) {
    String[] returningAttributes = null;
    if (parameters.containsKey("attr")) {
      List<String> value = parameters.get("attr");
      returningAttributes = value.toArray(new String[value.size()]);
    }
    return returningAttributes;
  }

  static SearchControls getSearchControls(Map<String, List<String>> parameters) {
    SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(getSearchScope(parameters));
    searchControls.setReturningAttributes(getReturnAttributes(parameters));
    return searchControls;
  }

  static String loadUtf8ResourceFromClasspath(String path) throws IOException {
    byte[] resourceBytes = loadResourceFromClasspath(path);
    return new String(resourceBytes, Charset.forName("UTF-8"));
  }

  static byte[] loadResourceFromClasspath(String path) throws IOException {
    LOGGER.fine(() -> {
      return String.format("Loading %s...", path);
    });
    byte[] contents = null;
    InputStream resource = IO.class.getResourceAsStream(path);
    try {
      if (resource == null) {
        LOGGER.warning(String.format(
          "%s not found! Returning empty byte array!", path));
        return new byte[0];
      }
      contents = toByteArray(resource);
    } finally {
      if (resource != null) {
        resource.close();
      }
    }
    LOGGER.fine(() -> {
      return String.format("%s loaded successfully!", path);
    });
    return contents;
  }

  static byte[] toByteArray(InputStream in) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[BUF_SZ];
    while (true) {
      int read = in.read(buf);
      if (read == -1) {
        break;
      }
      bos.write(buf, 0, read);
    }
    return bos.toByteArray();    
  }
}