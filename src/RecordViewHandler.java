import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.SearchControls;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class RecordViewHandler implements HttpHandler {

  private static final Logger LOGGER = Logger.getLogger(
    RecordViewHandler.class.getName());

  private static final String ERROR_HTML_TEMPLATE = "<html><head><title>" +
    "%1$d %2$s</title></head><body><h1>%2$s</h1><p>%3$s</p></body></html>";

  private static final String HTML_DOC_TEMPLATE = 
    "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n%1$s\n</head>\n<body>\n" +
      "%2$s\n</body>\n</html>";

  private static final String HTML_RESULTS_TEMPLATE = 
    "<table>\n<thead>\n<tr>\n<th scope=\"col\">Attribute</th>\n<th>Value" +
      "</th>\n</tr>\n</thead>\n<tbody>\n%1$s</tbody>\n</table>";

  private static final String HTML_TABLE_ROW_1_TEMPLATE = 
    "<tr style=\"rowColor\">\n<td>%1$s</td>\n<td>%2$s</td>\n</tr>\n";

  private static final String HTML_TABLE_ROW_2_TEMPLATE = 
    "<tr style=\"altColor\">\n<td>%1$s</td>\n<td>%2$s</td>\n</tr>\n";

  private static final String HTTP_DATE_LOG_FORMAT = "[dd/MMM/yyyy HH:mm:ss]";

  private final LdapSession ldapSession;

  private final String serverImplName;

  RecordViewHandler(LdapSession ldapSession, String serverImplName) {
    this.ldapSession = ldapSession;
    this.serverImplName = serverImplName;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    byte[] content = new byte[0];
    int status = 200;
    String contentType = ContentTypes.TYPES.get("html");
    if (!exchange.getRequestMethod().equals("GET")) {
      status = 405;
      content = getErrorHtml(status, "Method Not Allowed", 
        exchange.getRequestMethod()).getBytes();
    } else if (exchange.getRequestURI().getPath().contains("/record/rdn")) {
      try {
        content = handleGetRecord(exchange);
      } catch (PartialResultException e) {
        LOGGER.log(Level.FINE, e, () -> {
          return String.format("Ignoring %s", e.getClass().getName());
        });
      } catch (NamingException e) {
        status = 500;
        content = getErrorHtml(status, "Internal Server Error", 
          "Internal failure").getBytes();
        LOGGER.log(Level.SEVERE, "Error occurred during LDAP search.", e);
      }
    } else {
      status = 400;
      content = getErrorHtml(status, "Bad Request", "Can't service request.")
        .getBytes();
    }

    try {
      Headers h = exchange.getResponseHeaders();
      h.add("Content-Type", contentType);
      h.add("Server", String.format("%s/Java %s", serverImplName, 
        System.getProperty("java.version")));
      exchange.sendResponseHeaders(status, content.length);
    } catch (IOException e) {
      LOGGER.warning(String.format("Problem sending response headers", e));
    }

    if (content.length > 0) {
      OutputStream out = exchange.getResponseBody();
      out.write(content);
      out.flush();
    }
    
    // Log the request
    LOGGER.info(
      String.format("%1$s - - %2$s \"%3$s %4$s?%5$s\" %6$d -", 
        exchange.getRemoteAddress().getAddress().toString(),
        new SimpleDateFormat(HTTP_DATE_LOG_FORMAT).format(new Date()), 
        exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
        exchange.getRequestURI().getRawQuery(), status));
    exchange.close();
  }

  private String getErrorHtml(int status, String error, String text) {
    return String.format(ERROR_HTML_TEMPLATE, status, error, text);
  }

  private byte[] handleGetRecord(HttpExchange exchange) throws NamingException {
    URI requestUri = exchange.getRequestURI();
    String rdn = getRdnFromPath(requestUri.getPath());
    String filter = getFilterFromQuery(requestUri.getRawQuery());
    SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    Collection<StringTuple> results = ldapSession.search(rdn, filter, searchControls);
    StringBuilder resultRows = new StringBuilder();
    int row = 0;
    for (StringTuple result : results) {
      String template = row % 2 == 0 ? HTML_TABLE_ROW_1_TEMPLATE : 
        HTML_TABLE_ROW_2_TEMPLATE;
      resultRows.append(String.format(template, result.s1, result.s2))
        .append("\n");
      row++;
    }
    String resultTable = String.format(HTML_RESULTS_TEMPLATE, 
      resultRows.toString());
    String title = String.format("<title>Results: %s</title>", filter);
    String content = String.format(HTML_DOC_TEMPLATE, title, resultTable);
    return content.getBytes();
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

  private String getFilterFromQuery(String query) {
    String filter = "(objectClass=*)";
    if (!Server.isNullOrEmpty(query)) {
      String[] params = query.split("=");
      try {
        filter = URLDecoder.decode(params[1], "UTF-8");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return filter;
  }
}