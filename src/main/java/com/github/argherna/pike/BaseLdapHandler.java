package com.github.argherna.pike;

import java.io.IOException;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

abstract class BaseLdapHandler implements HttpHandler {

  private static final Logger LOGGER = Logger.getLogger(BaseLdapHandler.class.getName());

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

  DirContext getLdapContext() throws IOException {
    try {
      var connection = Settings.getConnectionSettings(Settings.getActiveConnectionName());
      var env = new Hashtable<String, Object>();
      env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
      env.put(Context.PROVIDER_URL, connection.getLdapUrl());
      var ldapContext = new InitialLdapContext(env, null);
      if (connection.getUseStartTls()) {
        LOGGER.fine("Starting TLS session...");
        var tls = (StartTlsResponse) ldapContext.extendedOperation(new StartTlsRequest());
        tls.negotiate();
      }
      ldapContext.addToEnvironment(Context.SECURITY_AUTHENTICATION, connection.getAuthType().toLowerCase());
      if (!connection.getAuthType().toLowerCase().equals("none")) {
        LOGGER.fine("Authenticating...");
        ldapContext.addToEnvironment(Context.SECURITY_PRINCIPAL, connection.getBindDn());
        ldapContext.addToEnvironment(Context.SECURITY_CREDENTIALS,
            new String(Settings.byteArrayToSecretText(connection.getBindDn(), connection.getPassword())));
      }
      ldapContext.addToEnvironment(Context.REFERRAL, connection.getReferralPolicy().toLowerCase());
      LOGGER.fine("Ldap context successfully created!");
      return ldapContext;
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      } else {
        throw new RuntimeException(e);
      }
    }
  }
}
