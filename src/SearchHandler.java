import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.rmi.Naming;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class SearchHandler extends BaseLdapHandler {

  private static final Function<String, List<String>> ATTRS_FUNCTION =
    s -> {
      try {
        return Arrays.asList(URLDecoder.decode(s, "UTF-8").split(" "));
      } catch (IOException e) {
        // Shouldn't happen, throw a RuntimeException so it's logged.
        throw new RuntimeException(e);
      }
    };

  private static final Logger LOGGER = Logger.getLogger(SearchHandler.class.getName());
  
  private static final Map<String, Function<String, List<String>>> PARAM_PROCS;

  static {
    Map<String, Function<String, List<String>>> paramProcs = new HashMap<>();
    paramProcs.put("attr", ATTRS_FUNCTION);
    PARAM_PROCS = Collections.unmodifiableMap(paramProcs);
  }

  @Override
  String getHtmlTemplateName() {
    return "templates/search.html";
  }

  @Override
  void doJson(HttpExchange exchange) throws IOException {
    HttpStatus status = HttpStatus.OK;
    String contentType = ContentTypes.TYPES.get("json");
    byte[] content = new byte[0];
    LdapContext ldapContext = getLdapContext();

    String rdn = null;
    String filter = null;
    List<String> attrs = null;
    String scope = null;
    String rawQuery = exchange.getRequestURI().getRawQuery();
    Map<String, List<String>> parameters = new HashMap<>();
    NamingEnumeration<SearchResult> results = null;
    try {
      if (rawQuery != null && !rawQuery.isEmpty()) {
        parameters = Http.queryToMap(rawQuery, PARAM_PROCS);
        rdn = parameters.containsKey("rdn") ? parameters.get("rdn").get(0) : 
          null;
        filter = Ldap.getFilter(parameters);
        attrs = parameters.get("attr");
        scope = parameters.containsKey("scope") ? 
          parameters.get("scope").get(0) : "subtree";
        
        String searchBase = getSearchBase(rdn);
        SearchControls searchControls = Ldap.getSearchControls(parameters);
        LOGGER.fine(String.format(
          "Searching with: base=%s,filter=%s,controls=%s", searchBase, filter, 
          Ldap.searchControlsToString(searchControls)));
        results = ldapContext.search(searchBase, filter, searchControls);
      }

      content = Json.renderSearch(Ldap.getLdapHost(
        Ldap.getContextInfo(ldapContext, Context.PROVIDER_URL)), 
        Ldap.getContextInfo(ldapContext, Context.SECURITY_PRINCIPAL), rdn, 
        filter, attrs, scope, results).getBytes();
      Http.sendResponse(exchange, status, content, contentType);
    } catch (NamingException e) {
      throw new RuntimeException(e);
    }
  }


  private String getSearchBase(String rdn) {
    if (Strings.isNullOrEmpty(rdn)) {
      return Pike.getActiveBaseDn();
    } else {
      StringJoiner sj = new StringJoiner(",");
      sj.add(rdn).add(Pike.getActiveBaseDn());
      return sj.toString();
    }
  }
}