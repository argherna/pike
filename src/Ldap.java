import java.io.IOException;
import java.net.URI;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

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
    return parameters.containsKey("filter") ? parameters.get("filter").get(0) : 
      "(objectClass=*)";
  }

  static int getSearchScope(Map<String, List<String>> parameters) {
    // Do a subtree search by default. If another (valid) scope is specified 
    // then search with that.
    int scope = SearchControls.SUBTREE_SCOPE;
    if (parameters.containsKey("scope")) {
      String value = parameters.get("scope").get(0);
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
      List<String> value = parameters.get("attr");
      if (value != null && !value.isEmpty()) {
        returningAttributes = value.toArray(new String[value.size()]);
      }
    }
    return returningAttributes;
  }

  static SearchControls getSearchControls(Map<String, List<String>> parameters) {
    SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(getSearchScope(parameters));
    searchControls.setReturningAttributes(getReturnAttributes(parameters));
    return searchControls;
  }

  static LdapContext createLdapContext(String connectionName) 
    throws IOException, NamingException, NoSuchAlgorithmException,
    CertificateException, KeyStoreException, UnrecoverableKeyException {
    Preferences connection = Settings.getConnectionSettings(connectionName);
    String ldapUrl = connection.get(Settings.LDAP_URL_SETTING, "");
    String baseDn = connection.get(Settings.BASE_DN_SETTING, "");
    String bindDn = connection.get(Settings.BIND_DN_SETTING, "");
    byte[] passwordBytes = connection.getByteArray(Settings.PASSWORD_SETTING, 
      new byte[0]);
    String password = new String(Settings.byteArrayToSecretText(bindDn, 
      passwordBytes));
    String authType = connection.get(Settings.AUTHTYPE_SETTING, "simple");
    String referralPolicy = 
      connection.get(Settings.REFERRAL_POLICY_SETTING, "ignore");
    boolean useStartTls = connection.getBoolean(Settings.USE_STARTTLS_SETTING,
      false);
    return createLdapContext(ldapUrl, baseDn, bindDn, password, authType, 
      referralPolicy, useStartTls);
  }

  static LdapContext createLdapContext(String ldapUrl, String baseDn,
    String bindDn, String password, String authType, 
    String referralPolicy, boolean useStartTls) 
    throws IOException, NamingException {
    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, 
      "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, ldapUrl);
    LdapContext ldapContext = new InitialLdapContext(env, null);
    if (useStartTls) {
      LOGGER.fine("Starting TLS session...");
      StartTlsResponse tls = (StartTlsResponse) ldapContext.extendedOperation(
        new StartTlsRequest());
      tls.negotiate();
    }
    ldapContext.addToEnvironment(Context.SECURITY_AUTHENTICATION, 
      authType.toLowerCase());
    if (!authType.equals("none")) {
      LOGGER.fine("Authenticating...");
      ldapContext.addToEnvironment(Context.SECURITY_PRINCIPAL, bindDn);
      ldapContext.addToEnvironment(Context.SECURITY_CREDENTIALS, password);
    }
    ldapContext.addToEnvironment(Context.REFERRAL, 
      referralPolicy.toLowerCase());
    LOGGER.fine("Ldap context successfully created!");
    return ldapContext;
  }

  static String searchControlsToString(SearchControls searchControls) {
    StringBuilder sc = new StringBuilder("SearchControls[");
    sc.append("search scope=")
      .append(searchScopeWords(searchControls.getSearchScope()))
      .append(",count limit=").append(searchControls.getCountLimit())
      .append(",deref link flag=").append(searchControls.getDerefLinkFlag())
      .append(",returning attributes=")
      .append(Arrays.toString(searchControls.getReturningAttributes()))
      .append(",returning object flag=")
      .append(searchControls.getReturningObjFlag())
      .append(",time limit=").append(searchControls.getTimeLimit())
      .append("]");
    return sc.toString();
  }

  private static String searchScopeWords(int searchScope) {
    String searchScopeWords = "UNKNOWN";
    switch (searchScope) {
      case 0:
        searchScopeWords = "OBJECT";
        break;
      case 1: 
        searchScopeWords = "ONELEVEL";
        break;
      case 2:
        searchScopeWords = "SUBTREE";
        break;
      default:
        break;
    }
    return searchScopeWords;
  }

  static String getContextInfo(LdapContext ldapContext, String envProperty) {
    try {
      Hashtable<?, ?> env = ldapContext.getEnvironment();
      return (String) env.get(envProperty);
    } catch (NamingException e) {
      LOGGER.log(Level.FINE, String.format("Failed to get %s, returning null.",
        envProperty), e);
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