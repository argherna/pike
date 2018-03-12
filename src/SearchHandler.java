import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.directory.SearchControls;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class SearchHandler implements HttpHandler {

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    byte[] content = new byte[0];
    HttpStatus status = HttpStatus.OK;
    String contentType = ContentTypes.TYPES.get("html");
    LdapSession ldapSession = (LdapSession) exchange.getHttpContext()
      .getAttributes().get("ldapSession");
    String rawQuery = exchange.getRequestURI().getRawQuery();
    if (Strings.isNullOrEmpty(rawQuery)) {
      content = Pages.searchForm(ldapSession.getHostname(), 
        ldapSession.getAuthentication()).getBytes();
    } else {
      Map<String, List<String>> parameters = IO.queryToMap(rawQuery);
      String rdn = parameters.get("rdn").get(0);
      String filter = IO.getFilter(parameters);
      SearchControls searchControls = IO.getSearchControls(parameters);
      Collection<StringTuple> results = new ArrayList<>();
      try {
        results = ldapSession.search(rdn, filter, searchControls);
        content = Pages.recordView(filter, results, ldapSession.getHostname(),
          ldapSession.getAuthentication()).getBytes();
      } catch (NamingException e) {
        throw new RuntimeException(e);
      }
    }

    IO.sendResponse(exchange, status, content, contentType);
  }
}