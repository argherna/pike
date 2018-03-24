import java.io.IOException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.directory.SearchControls;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class SearchHandler implements HttpHandler {

  private static final Function<String, List<String>> ATTRS_FUNCTION =
    s -> {
      try {
        return Arrays.asList(URLDecoder.decode(s, "UTF-8").split(" "));
      } catch (IOException e) {
        // Shouldn't happen, throw a RuntimeException so it's logged.
        throw new RuntimeException(e);
      }
    };
  
  private static final Map<String, Function<String, List<String>>> PARAM_PROCS;

  static {
    Map<String, Function<String, List<String>>> paramProcs = new HashMap<>();
    paramProcs.put("attr", ATTRS_FUNCTION);
    PARAM_PROCS = Collections.unmodifiableMap(paramProcs);
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    byte[] content = new byte[0];
    HttpStatus status = HttpStatus.OK;
    String contentType = ContentTypes.TYPES.get("html");
    LdapSession ldapSession = (LdapSession) exchange.getHttpContext()
      .getAttributes().get("ldapSession");
    if (ldapSession == null) {
      Http.sendResponseWithLocationNoContent(exchange, 
        HttpStatus.TEMPORARY_REDIRECT, contentType, "/connections");
      return;
    }
    String rawQuery = exchange.getRequestURI().getRawQuery();
    if (Strings.isNullOrEmpty(rawQuery)) {
      content = Pages.searchForm(ldapSession.getHostname(), 
        ldapSession.getAuthentication()).getBytes();
    } else {
      Map<String, List<String>> parameters = Http.queryToMap(rawQuery, 
        PARAM_PROCS);
      String rdn = parameters.containsKey("rdn") ? 
        parameters.get("rdn").get(0) : "";
      String filter = Ldap.getFilter(parameters);
      SearchControls searchControls = Ldap.getSearchControls(parameters);
      Map<String, Collection<StringTuple>> results = new HashMap<>();
      try {
        results = ldapSession.search(rdn, filter, searchControls);
        String attrsToReturn = null;
        if (parameters.containsKey("attr")) {
          StringJoiner attrs = new StringJoiner(" ");
          for (String attr : parameters.get("attr")) {
            attrs.add(attr);
          }
          attrsToReturn = attrs.toString();
        }
        content = Pages.resultsView(filter, results, ldapSession.getHostname(),
          ldapSession.getAuthentication(), rdn, attrsToReturn).getBytes();
      } catch (NamingException e) {
        throw new RuntimeException(e);
      }
    }

    Http.sendResponse(exchange, status, content, contentType);
  }
}