import java.io.IOException;
import java.util.List;
import java.util.StringJoiner;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapContext;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class RecordViewHandler extends BaseLdapHandler {

  private static final Logger LOGGER = Logger.getLogger(
    RecordViewHandler.class.getName());

  @Override
  String getHtmlTemplateName() {
    return "templates/record.html";
  }

  @Override
  void doJson(HttpExchange exchange) throws IOException {
    LdapContext ldapContext = getLdapContext();
    Attributes attributes = null;
    String dn = getDnFromPath(
      getLastPathComponent(exchange.getRequestURI().getPath()));
    String contentType = ContentTypes.TYPES.get("json");
    HttpStatus status = HttpStatus.OK;
    byte[] content = new byte[0];
    try {
      LOGGER.fine(() -> String.format("Looking up %s", dn));
      attributes = ldapContext.getAttributes(dn);
      content = Json.renderRecord(dn, attributes).getBytes();
    } catch (NamingException e) {
      throw new RuntimeException(e);
    }
    Http.sendResponse(exchange, status, content, contentType);
  }

  private String getLastPathComponent(String uriPath) {
    String[] pathComponents = uriPath.split("/");
    return pathComponents[pathComponents.length - 1];
  }

  private String getDnFromPath(String path) {
    String[] baseComponents = path.split(";");
    StringJoiner joiner = new StringJoiner(",");
    List.of(baseComponents).stream().filter(c -> c.indexOf("=") != -1)
      .forEach(c -> joiner.add(c));
    return joiner.toString();
  }
}