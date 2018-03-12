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

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    byte[] content = new byte[0];
    HttpStatus status = HttpStatus.OK;
    String contentType = ContentTypes.TYPES.get("html");
    String pathParam = getLastPathComponent(
      exchange.getRequestURI().getPath());
    LdapSession ldapSession = (LdapSession) exchange.getHttpContext()
      .getAttributes().get("ldapSession");
    if (pathParam.equals("rdn") || pathParam.contains("rdn;")) {
      try {
        content = handleGetRecord(exchange);
      } catch (PartialResultException e) {
        LOGGER.log(Level.FINE, e, () -> {
          return String.format("Ignoring %s", e.getClass().getName());
        });
      } catch (InvalidNameException e) {
        status = HttpStatus.NOT_FOUND;
        content = Pages.errorHtml(status, 
          "No records found for the given filter.",
          ldapSession.getHostname(), ldapSession.getAuthentication())
          .getBytes();
      } catch (NamingException e) {
        throw new RuntimeException(e);
      }
    } else {
      status = HttpStatus.BAD_REQUEST;
      content = Pages.errorHtml(status, "Cannot service request.", 
      ldapSession.getHostname(), ldapSession.getAuthentication()).getBytes();
    }
    
    IO.sendResponse(exchange, status, content, contentType);
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
      IO.queryToMap(requestUri.getRawQuery());
    String filter = IO.getFilter(parameters);
    SearchControls searchControls = IO.getSearchControls(parameters);
    LdapSession ldapSession = (LdapSession) exchange.getHttpContext()
      .getAttributes().get("ldapSession");
    Collection<StringTuple> results = ldapSession.search(rdn, filter, 
      searchControls);
    return Pages.recordView(filter, results, ldapSession.getHostname(),
      ldapSession.getAuthentication()).getBytes();
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
}