package com.github.argherna.pike;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapContext;

import com.sun.net.httpserver.HttpExchange;

class RecordViewHandler extends BaseLdapHandler {

  @Override
  String getHtmlTemplateName() {
    return "/templates/record.html";
  }

  @Override
  void doJson(HttpExchange exchange) throws IOException {
    LdapContext ldapContext = getLdapContext();
    Attributes attributes = null;
    String dn = getDnFromPath(Http.getLastPathComponent(exchange.getRequestURI().getPath()));
    String contentType = ContentTypes.TYPES.get("json");
    HttpStatus status = HttpStatus.OK;
    byte[] content = new byte[0];
    try {
      attributes = ldapContext.getAttributes(dn);
      content = Json
          .renderObject(Map.of("connection", Settings.getConnectionSettingsAsMap(Settings.getActiveConnectionName()),
              "record", Maps.toMap(dn, attributes)))
          .getBytes();
    } catch (NamingException e) {
      throw new RuntimeException(e);
    }
    Http.sendResponse(exchange, status, content, contentType);
  }

  private String getDnFromPath(String path) {
    StringJoiner joiner = new StringJoiner(",");
    List.of(path.split(";")).stream().filter(c -> c.indexOf("=") != -1).forEach(c -> joiner.add(c));
    return joiner.toString();
  }
}
