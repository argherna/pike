import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import com.sun.net.httpserver.HttpExchange;

class SearchHandler extends BaseLdapHandler {

  private static final Function<String, List<String>> ATTRS_FUNCTION = s -> {
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
  String getHtmlTemplateName() {
    return "templates/search.html";
  }

  @Override
  void doJson(HttpExchange exchange) throws IOException {
    HttpStatus status = HttpStatus.OK;
    String contentType = ContentTypes.TYPES.get("json");
    byte[] content = new byte[0];
    LdapContext ldapContext = getLdapContext();

    String rdn = "";
    String filter = "";
    List<String> attrs = List.of();
    String scope = "";
    String rawQuery = exchange.getRequestURI().getRawQuery();
    Map<String, List<String>> parameters = new HashMap<>();
    NamingEnumeration<SearchResult> results = null;
    List<Map<String, Object>> records = List.of();
    try {
      if (rawQuery != null && !rawQuery.isEmpty()) {
        parameters = Http.queryToMap(rawQuery, PARAM_PROCS);
        rdn = parameters.containsKey("rdn") ? parameters.get("rdn").get(0) : null;
        filter = Ldap.getFilter(parameters);
        attrs = parameters.get("attr");
        scope = parameters.containsKey("scope") ? parameters.get("scope").get(0) : "subtree";

        String searchBase = getSearchBase(rdn);
        SearchControls searchControls = Ldap.getSearchControls(parameters);
        results = ldapContext.search(searchBase, filter, searchControls);
        if (results.hasMoreElements()) {
          records = new ArrayList<>();
          while (results.hasMore()) {
            SearchResult result = results.next();
            records.add(Maps.toMap(result.getNameInNamespace(), result.getAttributes()));
          }
        }
      }

      Map<String, Object> data = new HashMap<>();
      data.put("connection", Settings.getConnectionSettingsAsMap(Settings.getActiveConnectionName()));
      Map<String, Object> params = new HashMap<>();
      if (!Strings.isNullOrEmpty(scope)) {
        params.put("searchScope", scope);
      }
      if (!Strings.isNullOrEmpty(filter)) {
        params.put("filter", filter);
      }
      if (!Strings.isNullOrEmpty(rdn)) {
        params.put("rdn", rdn);
      }
      if (attrs != null && !attrs.isEmpty()) {
        params.put("attrs", attrs);
      }

      if (!params.isEmpty()) {
        data.put("parameters", params);
      }

      if (!records.isEmpty()) {
        data.put("records", records);
      }

      content = Json.renderObject(data).getBytes();
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
