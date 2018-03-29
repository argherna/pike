import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;

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
      returningAttributes = value.toArray(new String[value.size()]);
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
    AuthType authType = AuthType.valueOf(
      connection.get(Settings.AUTHTYPE_SETTING, "").toUpperCase());
    ReferralPolicy referralPolicy = ReferralPolicy.valueOf(
      connection.get(Settings.REFERRAL_POLICY_SETTING, "").toUpperCase());
    boolean useStartTls = connection.getBoolean(Settings.USE_STARTTLS_SETTING,
      false);
    return createLdapContext(ldapUrl, baseDn, bindDn, password, authType, 
      referralPolicy, useStartTls);
  }

  static LdapContext createLdapContext(String ldapUrl, String baseDn,
    String bindDn, String password, AuthType authType, 
    ReferralPolicy referralPolicy, boolean useStartTls) 
    throws IOException, NamingException {
    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, 
      "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, ldapUrl);
    LdapContext ldapContext = new InitialLdapContext(env, null);
    if (useStartTls) {
      LOGGER.config("Starting TLS session...");
      ldapContext.extendedOperation(new StartTlsRequest());
    }
    ldapContext.addToEnvironment(Context.SECURITY_AUTHENTICATION, 
      authType.toString().toLowerCase());
    if (authType != AuthType.NONE) {
      ldapContext.addToEnvironment(Context.SECURITY_PRINCIPAL, bindDn);
      ldapContext.addToEnvironment(Context.SECURITY_CREDENTIALS, password);
    }
    ldapContext.addToEnvironment(Context.REFERRAL, 
      referralPolicy.toString().toLowerCase());
    return ldapContext;
  }
}