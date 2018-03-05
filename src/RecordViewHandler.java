import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
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

  private static int BUF_SZ = 0x1000;

  private static final Logger LOGGER = Logger.getLogger(
    RecordViewHandler.class.getName());

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
    String errorHeaderTemplate = getTemplateAsUtf8String("error-head");
    String errorHeader = String.format(errorHeaderTemplate, status, error);
    
    String errorBodyTemplate = getTemplateAsUtf8String("error-body");
    String errorBody = String.format(errorBodyTemplate, error, text);

    String htmlTemplate = getTemplateAsUtf8String("basedoc");
    return String.format(htmlTemplate, errorHeader, errorBody);
  }

  private byte[] handleGetRecord(HttpExchange exchange) 
    throws NamingException {
    URI requestUri = exchange.getRequestURI();
    String rdn = getRdnFromPath(requestUri.getPath());
    String filter = getFilterFromQuery(requestUri.getRawQuery());
    SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    Collection<StringTuple> results = ldapSession.search(rdn, filter, 
      searchControls);
    
    String recordRowTemplate = getTemplateAsUtf8String("record-table-row");
    StringBuilder recordRows = new StringBuilder();
    int row = 0;
    String rowStyle = "rowColor";
    LOGGER.fine("Generating table rows");
    for (StringTuple result : results) {
      rowStyle = row % 2 == 0 ? "rowColor" : "altColor";
      recordRows.append(String.format(recordRowTemplate, rowStyle, result.s1, 
        result.s2)).append("\n");
      row++;
    }

    String tableTemplate = getTemplateAsUtf8String("record-table");
    String recordTable = String.format(tableTemplate, recordRows.toString());
    
    String recordHeadTemplate = getTemplateAsUtf8String("record-head");
    String recordHead = String.format(recordHeadTemplate, filter);

    String recordBodyTemplate = getTemplateAsUtf8String("record-body");
    String recordBody = String.format(recordBodyTemplate, filter, recordTable);

    String basedocTemplate = getTemplateAsUtf8String("basedoc");
    String content = String.format(basedocTemplate, recordHead, recordBody);
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

  private String getTemplateAsUtf8String(String name) {
    byte[] templateBytes = getTemplateAsBytes(name);
    return new String(templateBytes, Charset.forName("UTF-8"));
  }

  private byte[] getTemplateAsBytes(String name) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (InputStream templateIs = RecordViewHandler.class.getResourceAsStream("templates/" + name + ".html")) {
      if (templateIs == null) {
        throw new RuntimeException(String.format("No template found for %s!", name));
      }
      byte[] buf = new byte[BUF_SZ];
      while (true) {
        int read = templateIs.read(buf);
        if (read == -1) {
          break;
        }
        bos.write(buf, 0, read);
      }
      return bos.toByteArray();
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, 
        String.format("Failure reading resource %s", name), e);
      throw new RuntimeException(e);
    }
  }
}