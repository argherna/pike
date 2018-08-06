package com.github.argherna.pike;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

import javax.naming.NamingException;

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
    var paramProcs = new HashMap<String, Function<String, List<String>>>();
    paramProcs.put("attr", ATTRS_FUNCTION);
    PARAM_PROCS = Collections.unmodifiableMap(paramProcs);
  }

  @Override
  String getHtmlTemplateName() {
    return "/templates/search.html";
  }

  @Override
  void doJson(HttpExchange exchange) throws IOException {
    var ldapContext = getLdapContext();

    var rdn = "";
    var filter = "";
    List<String> attrs = List.of();
    var scope = "";
    var rawQuery = exchange.getRequestURI().getRawQuery();
    Map<String, List<String>> parameters = new HashMap<>();
    List<Map<String, Object>> records = List.of();
    try {
      if (rawQuery != null && !rawQuery.isEmpty()) {
        parameters = Http.queryToMap(rawQuery, PARAM_PROCS);
        rdn = parameters.containsKey("rdn") ? parameters.get("rdn").get(0) : null;
        filter = Ldap.getFilter(parameters);
        attrs = parameters.get("attr");
        scope = parameters.containsKey("scope") ? parameters.get("scope").get(0) : "subtree";

        var searchBase = getSearchBase(rdn);
        var searchControls = Ldap.getSearchControls(parameters);
        var results = ldapContext.search(searchBase, filter, searchControls);
        if (results.hasMoreElements()) {
          records = new ArrayList<>();
          while (results.hasMore()) {
            var result = results.next();
            records.add(Maps.toMap(result.getNameInNamespace(), result.getAttributes()));
          }
        }
      }

      var data = new HashMap<String, Object>();
      data.put("connection", Maps.toMap(Settings.getConnectionSettings(Settings.getActiveConnectionName())));
      
      var params = new HashMap<String, Object>();
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

      Http.sendResponse(exchange, HttpStatus.OK, Json.renderObject(data).getBytes(), ContentTypes.TYPES.get("json"));
    } catch (NamingException e) {
      throw new RuntimeException(e);
    }
  }

  private String getSearchBase(String rdn) {
    if (Strings.isNullOrEmpty(rdn)) {
      return Pike.getActiveBaseDn();
    } else {
      var sj = new StringJoiner(",");
      sj.add(rdn).add(Pike.getActiveBaseDn());
      return sj.toString();
    }
  }
}
