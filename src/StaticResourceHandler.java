import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class StaticResourceHandler implements HttpHandler {

  private static final Logger LOGGER = Logger.getLogger(
    StaticResourceHandler.class.getName());

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    HttpStatus status = HttpStatus.OK;
    byte[] content = new byte[0];
    String contentType = null;
    String path = exchange.getRequestURI().getPath();
    String extension = getFileExtension(path);
    contentType = ContentTypes.TYPES.get(extension);
    content = path.startsWith("/") ? IO.loadResourceFromClasspath(
      path.substring(1)) : IO.loadResourceFromClasspath(path);
    if (content.length == 0) {
      status = HttpStatus.NOT_FOUND;
      LdapSession ldapSession = (LdapSession) exchange.getHttpContext()
        .getAttributes().get("ldapSession");
      content = Pages.errorHtml(status, String.format(
        "%s not found on this server", path), ldapSession.getHostname(),
        ldapSession.getAuthentication()).getBytes();
      contentType = ContentTypes.TYPES.get("html");
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
    exchange.close();
  }

  private String getFileExtension(String filename) {
    int lastDot = filename.lastIndexOf('.');
    return lastDot > 0 ? filename.substring(lastDot + 1) : "";
  }
}
