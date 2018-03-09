import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.InvalidNameException;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.SearchControls;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class RecordViewHandler implements HttpHandler {

  private static final Logger LOGGER = Logger.getLogger(
    RecordViewHandler.class.getName());

  private final LdapSession ldapSession;

  RecordViewHandler(LdapSession ldapSession) {
    this.ldapSession = ldapSession;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    byte[] content = new byte[0];
    HttpStatus status = HttpStatus.OK;
    String contentType = ContentTypes.TYPES.get("html");
    String pathParam = getLastPathComponent(
      exchange.getRequestURI().getPath());
    if (!exchange.getRequestMethod().equals("GET")) {
      status = HttpStatus.METHOD_NOT_ALLOWED;
      content = Pages.errorHtml(status, exchange.getRequestMethod())
        .getBytes();
    } else if (pathParam.equals("rdn") || pathParam.contains("rdn;")) {
      try {
        content = handleGetRecord(exchange);
      } catch (PartialResultException e) {
        LOGGER.log(Level.FINE, e, () -> {
          return String.format("Ignoring %s", e.getClass().getName());
        });
      } catch (InvalidNameException e) {
        status = HttpStatus.NOT_FOUND;
        content = Pages.errorHtml(status, 
          "No records found for the given filter.").getBytes();
      } catch (NamingException e) {
        status = HttpStatus.INTERNAL_SERVER_ERROR;
        content = Pages.errorHtml(status, "Internal failure").getBytes();
        LOGGER.log(Level.SEVERE, "Error occurred during LDAP search.", e);
      }
    } else {
      status = HttpStatus.BAD_REQUEST;
      content = Pages.errorHtml(status, "Cannot service request.").getBytes();
    }
    
    try {
      IO.sendResponseHeaders(exchange, contentType, status.getStatusCode(),
        content.length);
    } catch (IOException e) {
      LOGGER.warning(String.format("Problem sending response headers", e));
    }

    if (content.length > 0) {
      OutputStream out = exchange.getResponseBody();
      out.write(content);
      out.flush();
      out.close();
    }
    
    // Log the request
    RequestLogger.log(status.getStatusCode(), exchange);
    exchange.close();
  }

  private String getLastPathComponent(String uriPath) {
    String[] pathComponents = uriPath.split("/");
    return pathComponents[pathComponents.length - 1];
  }

  private byte[] handleGetRecord(HttpExchange exchange) 
    throws NamingException {
    URI requestUri = exchange.getRequestURI();
    String rdn = getRdnFromPath(requestUri.getPath());
    Map<String, List<String>> parameters = 
      queryToMap(requestUri.getRawQuery());
    String filter = getFilter(parameters);
    SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(getSearchScope(parameters));
    searchControls.setReturningAttributes(getReturnAttributes(parameters));
    Collection<StringTuple> results = ldapSession.search(rdn, filter, 
      searchControls);
    return Pages.recordView(filter, results).getBytes();
  }

  private String getRdnFromPath(String path) {
    String[] baseComponents = path.split(";");
    StringJoiner joiner = new StringJoiner(",");
    for (String baseComponent : baseComponents) {
      if (!baseComponent.startsWith("/")) {
        joiner.add(baseComponent);
      }
    }
    return joiner.toString();
  }

  private Map<String, List<String>> queryToMap(String rawQuery) {
    Map<String, List<String>> decodedParameters = new HashMap<>();
    if (!Server.isNullOrEmpty(rawQuery)) {
      String[] parameters = rawQuery.split("&");
      for (String parameter : parameters) {
        String[] param = parameter.split("=");
        try {
          if (decodedParameters.containsKey(param[0])) {
            List<String> value = decodedParameters.get(param[0]);
            value.add(URLDecoder.decode(param[1], "UTF-8"));
            decodedParameters.replace(param[0], value);
          } else {
            List<String> value = new ArrayList<>();
            value.add(URLDecoder.decode(param[1], "UTF-8"));
            decodedParameters.put(param[0], value);
          }
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return decodedParameters;
  }

  private String getFilter(Map<String, List<String>> parameters) {
    return parameters.containsKey("filter") ? parameters.get("filter").get(0) : 
      "(objectClass=*)";
  }

  private int getSearchScope(Map<String, List<String>> parameters) {
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

  private String[] getReturnAttributes(Map<String, List<String>> parameters) {
    String[] returningAttributes = null;
    if (parameters.containsKey("attr")) {
      List<String> value = parameters.get("attr");
      returningAttributes = value.toArray(new String[value.size()]);
    }
    return returningAttributes;
  }
}