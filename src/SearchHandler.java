import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.rmi.Naming;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.LdapContext;

import com.sun.net.httpserver.Headers;
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

  private static final Logger LOGGER = Logger.getLogger(SearchHandler.class.getName());
  
  private static final Map<String, Function<String, List<String>>> PARAM_PROCS;

  static {
    Map<String, Function<String, List<String>>> paramProcs = new HashMap<>();
    paramProcs.put("attr", ATTRS_FUNCTION);
    PARAM_PROCS = Collections.unmodifiableMap(paramProcs);
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    Headers headers = exchange.getRequestHeaders();
    if (headers.containsKey("Accept")) {
      List<String> accept = headers.get("Accept");
      if (accept.contains(ContentTypes.TYPES.get("json"))) {
        doJson(exchange);
      } else {
        doHtml(exchange);
      }
    } else {
      doHtml(exchange);
    }
    // HttpStatus status = HttpStatus.OK;
    // String contentType = ContentTypes.TYPES.get("html");
    // LdapContext ldapContext = null;
    // try {
    //   ldapContext = Pike.getActiveLdapContext();
    // } catch (Exception e) {
    //   if (e instanceof IOException) {
    //     throw (IOException) e;
    //   } else {
    //     throw new RuntimeException(e);
    //   }
    // }
    // if (ldapContext == null) {
    //   Http.sendResponseWithLocationNoContent(exchange, 
    //     HttpStatus.TEMPORARY_REDIRECT, contentType, "/connections");
    //   return;
    // }
    // String rawQuery = exchange.getRequestURI().getRawQuery();
    // if (Strings.isNullOrEmpty(rawQuery)) {
    //   content = Html.searchForm(getLdapHost(
    //     getContextInfo(ldapContext, Context.PROVIDER_URL)), 
    //     getContextInfo(ldapContext, Context.SECURITY_PRINCIPAL)).getBytes();
    // } else {
    //   Map<String, List<String>> parameters = Http.queryToMap(rawQuery, 
    //     PARAM_PROCS);
    //   String rdn = parameters.containsKey("rdn") ? 
    //     parameters.get("rdn").get(0) : "";
    //   String filter = Ldap.getFilter(parameters);
    //   SearchControls searchControls = Ldap.getSearchControls(parameters);
    //   Map<String, Collection<StringTuple>> results = new HashMap<>();
    //   try {
    //     results = ldapSession.search(rdn, filter, searchControls);
    //     String attrsToReturn = null;
    //     if (parameters.containsKey("attr")) {
    //       StringJoiner attrs = new StringJoiner(" ");
    //       for (String attr : parameters.get("attr")) {
    //         attrs.add(attr);
    //       }
    //       attrsToReturn = attrs.toString();
    //     }
    //     content = Html.resultsView(filter, results, ldapSession.getHostname(),
    //       ldapSession.getAuthentication(), rdn, attrsToReturn).getBytes();
    //   } catch (NamingException e) {
    //     throw new RuntimeException(e);
    //   }
    }

  //   Http.sendResponse(exchange, status, content, contentType);
  // }

  private void doHtml(HttpExchange exchange) throws IOException {
    HttpStatus status = HttpStatus.OK;
    String contentType = ContentTypes.TYPES.get("html");
    LdapContext ldapContext = null;
    try {
      ldapContext = Pike.getActiveLdapContext();
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      } else {
        throw new RuntimeException(e);
      }
    }
    if (ldapContext == null) {
      Http.sendResponseWithLocationNoContent(exchange, 
        HttpStatus.TEMPORARY_REDIRECT, contentType, "/connections");
      return;
    }

    byte[] content = IO.loadResourceFromClasspath("templates/search.html");
    Http.sendResponse(exchange, status, content, contentType);
  }

  private void doJson(HttpExchange exchange) throws IOException {
    HttpStatus status = HttpStatus.OK;
    String contentType = ContentTypes.TYPES.get("json");
    LdapContext ldapContext = null;
    try {
      ldapContext = Pike.getActiveLdapContext();
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      } else {
        throw new RuntimeException(e);
      }
    }

    if (ldapContext == null) {
      Http.sendResponseWithLocationNoContent(exchange, 
        HttpStatus.TEMPORARY_REDIRECT, contentType, "/connections");
      return;
    }

    String rdn = null;
    String filter = null;
    List<String> attrs = null;
    String scope = null;
    String rawQuery = exchange.getRequestURI().getRawQuery();
    if (rawQuery != null && !rawQuery.isEmpty()) {
      Map<String, List<String>> parameters = Http.queryToMap(rawQuery, 
        PARAM_PROCS);
      rdn = parameters.containsKey("rdn") ? parameters.get("rdn").get(0) : 
        null;
      filter = Ldap.getFilter(parameters);
      attrs = parameters.get("attr");
      scope = parameters.containsKey("scope") ? 
        (!parameters.get("scope").get(0).equals("Search Scope...") ? 
          parameters.get("scope").get(0) : "subtree") : null;
    }

    try {

      byte[] content = Json.renderSearch(
        getLdapHost(getContextInfo(ldapContext, Context.PROVIDER_URL)), 
        getContextInfo(ldapContext, Context.SECURITY_PRINCIPAL), rdn, 
        filter, attrs, scope, null).getBytes();
      Http.sendResponse(exchange, status, content, contentType);
    } catch (NamingException e) {
      throw new RuntimeException(e);
    }
  }

  private String getContextInfo(LdapContext ldapContext, String envProperty) {
    try {
      Hashtable<?, ?> env = ldapContext.getEnvironment();
      return (String) env.get(envProperty);
    } catch (NamingException e) {
      LOGGER.log(Level.INFO, String.format("Failed to get %s, returning null.",
        envProperty), e);
      return null;
    }
  }

  private String getLdapHost(String ldapUrl) {
    if (Strings.isNullOrEmpty(ldapUrl)) {
      return "unknown";
    }
    return URI.create(ldapUrl).getHost();
  }
}