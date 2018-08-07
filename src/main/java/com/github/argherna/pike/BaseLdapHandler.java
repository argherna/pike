package com.github.argherna.pike;

import java.io.IOException;
import java.util.List;

import javax.naming.ldap.LdapContext;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

abstract class BaseLdapHandler implements HttpHandler {

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    var headers = exchange.getRequestHeaders();
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
  }

  void doHtml(HttpExchange exchange) throws IOException {
    internalDoHtml(exchange);
  }

  private void internalDoHtml(HttpExchange exchange) throws IOException {
    // Indirection keeps logic from being overridden in subclasses.
    var status = HttpStatus.OK;
    var contentType = ContentTypes.TYPES.get("html");
    var activeConnectionName = Settings.getActiveConnectionName();
    if (Strings.isNullOrEmpty(activeConnectionName)) {
      Http.sendResponseWithLocationNoContent(exchange, HttpStatus.TEMPORARY_REDIRECT, contentType, "/connections");
      return;
    }

    Http.sendResponse(exchange, status, IO.loadResourceFromClasspath(getHtmlTemplateName()), contentType);
  }

  abstract String getHtmlTemplateName();

  abstract void doJson(HttpExchange exchange) throws IOException;

  LdapContext getLdapContext() throws IOException {
    try {
      return Ldap.createLdapContext(Settings.getActiveConnectionName());
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      } else {
        throw new RuntimeException(e);
      }
    }
  }
}
