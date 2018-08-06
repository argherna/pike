package com.github.argherna.pike;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import javax.naming.NamingException;

import com.sun.net.httpserver.HttpExchange;

class RecordViewHandler extends BaseLdapHandler {

  @Override
  String getHtmlTemplateName() {
    return "/templates/record.html";
  }

  @Override
  void doJson(HttpExchange exchange) throws IOException {
    var ldapContext = getLdapContext();
    var dn = getDnFromPath(Http.getLastPathComponent(exchange.getRequestURI().getPath()));
    var content = new byte[0];
    try {
      var attributes = ldapContext.getAttributes(dn);
      content = Json.renderObject(
          Map.of("connection", Maps.toMap(Settings.getConnectionSettings(Settings.getActiveConnectionName())), "record",
              Maps.toMap(dn, attributes)))
          .getBytes();
    } catch (NamingException e) {
      throw new RuntimeException(e);
    }
    Http.sendResponse(exchange, HttpStatus.OK, content, ContentTypes.TYPES.get("json"));
  }

  private String getDnFromPath(String path) {
    var joiner = new StringJoiner(",");
    List.of(path.split(";")).stream().filter(c -> c.indexOf("=") != -1).forEach(c -> joiner.add(c));
    return joiner.toString();
  }
}
