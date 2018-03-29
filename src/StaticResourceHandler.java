import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class StaticResourceHandler implements HttpHandler {

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    HttpStatus status = HttpStatus.OK;
    String path = exchange.getRequestURI().getPath();
    String extension = getFileExtension(path);
    String contentType = ContentTypes.TYPES.get(extension);
    byte[] content = path.startsWith("/") ? IO.loadResourceFromClasspath(
      path.substring(1)) : IO.loadResourceFromClasspath(path);
    if (content.length == 0) {
      status = HttpStatus.NOT_FOUND;
      LdapSession ldapSession = (LdapSession) exchange.getHttpContext()
        .getAttributes().get("ldapSession");
      // Avoid NPE by rendering the error page without LDAP session information.
      if (ldapSession != null) {
        content = Html.renderError(status, String.format(
          "%s not found on this server", path), ldapSession.getHostname(),
          ldapSession.getAuthentication()).getBytes();
      } else {
        content = Html.renderError(status, String.format(
          "%s not found on this server", path)).getBytes();
      }
      contentType = ContentTypes.TYPES.get("html");
    }

    Http.sendResponse(exchange, status, content, contentType);
  }

  private String getFileExtension(String filename) {
    int lastDot = filename.lastIndexOf('.');
    return lastDot > 0 ? filename.substring(lastDot + 1) : "";
  }
}
