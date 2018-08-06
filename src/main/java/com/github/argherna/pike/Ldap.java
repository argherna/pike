package com.github.argherna.pike;

import java.io.IOException;
import java.net.URI;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;

final class Ldap {

  private static final Logger LOGGER = Logger.getLogger(Ldap.class.getName());

  private Ldap() {
    // Empty constructor prevents instantiation.
  }

  static String getFilter(Map<String, List<String>> parameters) {
    return parameters.containsKey("filter") ? parameters.get("filter").get(0) : "(objectClass=*)";
  }

  static int getSearchScope(Map<String, List<String>> parameters) {
    // Do a subtree search by default. If another (valid) scope is specified
    // then search with that.
    var scope = SearchControls.SUBTREE_SCOPE;
    if (parameters.containsKey("scope")) {
      var value = parameters.get("scope").get(0);
      if (value.equalsIgnoreCase("object")) {
        scope = SearchControls.OBJECT_SCOPE;
      } else if (value.equalsIgnoreCase("onelevel")) {
        scope = SearchControls.ONELEVEL_SCOPE;
      }
    }
    return scope;
  }

  static String[] getReturnAttributes(Map<String, List<String>> parameters) {
    String[] returningAttributes = null;
    if (parameters.containsKey("attr")) {
      var value = parameters.get("attr");
      if (value != null && !value.isEmpty()) {
        returningAttributes = value.toArray(new String[value.size()]);
      }
    }
    return returningAttributes;
  }

  static SearchControls getSearchControls(Map<String, List<String>> parameters) {
    var searchControls = new SearchControls();
    searchControls.setSearchScope(getSearchScope(parameters));
    searchControls.setReturningAttributes(getReturnAttributes(parameters));
    return searchControls;
  }

  static LdapContext createLdapContext(String connectionName) throws IOException, NamingException,
      NoSuchAlgorithmException, CertificateException, KeyStoreException, UnrecoverableKeyException {
    var connection = Settings.getConnectionSettings(connectionName);
    var env = new Hashtable<String, String>();
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
  }

  static String getContextInfo(Context context, String envProperty) {
    try {
      var env = context.getEnvironment();
      return (String) env.get(envProperty);
    } catch (NamingException e) {
      LOGGER.log(Level.FINE, String.format("Failed to get %s, returning null.", envProperty), e);
      return null;
    }
  }

  static String getLdapHost(String ldapUrl) {
    if (Strings.isNullOrEmpty(ldapUrl)) {
      return "unknown";
    }
    return URI.create(ldapUrl).getHost();
  }
}
