import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.SSLSession;

class LdapSession {

  private static final Logger LOGGER = Logger.getLogger(
      LdapSession.class.getName());

  private final LdapContext ldapContext;
  
  private final StartTlsResponse tls;

  private final String baseDn;

  private final SSLSession sslSession;

  LdapSession(String ldapUrl, String baseDn, String bindDn, 
    String password, boolean useStartTls) {
    this.baseDn = baseDn;
    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, 
      "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, ldapUrl);
    try {
      ldapContext = new InitialLdapContext(env, null);
      if (useStartTls) {
        LOGGER.config("Starting TLS session...");
        tls = (StartTlsResponse) ldapContext.extendedOperation(
                new StartTlsRequest());
        sslSession = tls.negotiate();
      } else {
        tls = null;
        sslSession = null;
      }
      ldapContext.addToEnvironment(Context.SECURITY_AUTHENTICATION, 
          "simple");
      ldapContext.addToEnvironment(Context.SECURITY_PRINCIPAL, 
          bindDn);
      ldapContext.addToEnvironment(Context.SECURITY_CREDENTIALS,
          password);
      ldapContext.addToEnvironment(Context.REFERRAL, "ignore");
      LOGGER.config(() -> {
        return String.format("Connected to %s; search base = %s", ldapUrl, 
          baseDn);
      });
      Runtime.getRuntime().addShutdownHook(new Thread() {
        
        @Override
        public void run() {
          shutdown();
        }
      });
    } catch (IOException | NamingException e) {
      LOGGER.severe(String.format("Error initializing LDAP connection: %s%n", e.getMessage()));
      throw new RuntimeException(e);
    }
  }

  private void shutdown() {
    LOGGER.warning("Disconnecting from LDAP Server...");
    if (sslSession != null) {
      sslSession.invalidate();
    }
    if (tls != null) {
      try {
        tls.close();
      } catch (IOException e) {
        LOGGER.warning(String.format("Failed to close TLS response: %s%n",
          e.getMessage()));
      }
    }
    try {
      ldapContext.close();
    } catch (NamingException e) {
      LOGGER.warning(String.format("Failed to close LDAP connection: %s%n", 
        e.getMessage()));
    }
  }

  Map<String, Collection<StringTuple>> search(String rdn, String filter, 
    SearchControls searchControls) throws NamingException {
    String searchBase = getSearchBase(rdn);
    LOGGER.fine(() -> {
      return String.format("Searching %s with filter %s", searchBase,
        filter);
    });

    NamingEnumeration<SearchResult> result = ldapContext.search(
      searchBase, filter, searchControls);
    Map<String, Collection<StringTuple>> searchResults = loadResults(result, 
      searchBase);
    return searchResults;
  }
  
  String getHostname() {
    try {
      Hashtable<?, ?> env = ldapContext.getEnvironment();
      String ldapUrl = (String) env.get(Context.PROVIDER_URL);
      return URI.create(ldapUrl).getHost();
    } catch (NamingException e) {
      LOGGER.log(Level.INFO, "Failed to get server host name, returning null.",
        e);
      return null;
    }
  }
  
  String getAuthentication() {
    try {
      Hashtable<?, ?> env = ldapContext.getEnvironment();
      return (String) env.get(Context.SECURITY_PRINCIPAL);
    } catch (NamingException e) {
      LOGGER.log(Level.INFO, "Failed to get authentication, returning null.",
        e);
      return null;
    }
  }
  
  private String getSearchBase(String rdn) {
    if (Strings.isNullOrEmpty(rdn)) {
      return baseDn;
    } else {
      StringJoiner searchBase = new StringJoiner(",");
      return searchBase.add(rdn).add(baseDn).toString();
    }
  }

  private Map<String, Collection<StringTuple>> loadResults(
    NamingEnumeration<SearchResult> results, String searchBase) 
    throws NamingException {

    Map<String, Collection<StringTuple>> records = new HashMap<>();
    while (results.hasMore()) {
      SearchResult sr = results.next();
      String dn = String.format("%s,%s", sr.getName(), searchBase);
      List<StringTuple> attributes = new ArrayList<>();
      Attributes attrs = sr.getAttributes();
      NamingEnumeration<String> attrNames = attrs.getIDs();
      while (attrNames.hasMore()) {
        String name = attrNames.next();
        attributes.addAll(attrsToTuples(attrs, name));
      }
      Collections.sort(attributes);
      records.put(dn, attributes);
    }
    return records;
  }

  private List<StringTuple> attrsToTuples(Attributes attrs, String name) 
    throws NamingException {
    List<StringTuple> attributes = new ArrayList<>();
    Attribute attr = attrs.get(name);
    NamingEnumeration<?> attrvals = attr.getAll();
    while (attrvals.hasMore()) {
      Object v = attrvals.next();
      StringTuple t = null;
      if (v instanceof String) {
        t = new StringTuple(attr.getID(), v.toString());
      } else {
        t = new StringTuple(attr.getID(), v.getClass().getSimpleName());
      }
      attributes.add(t);
    }
    return attributes;
  }
}