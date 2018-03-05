import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
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

  Collection<StringTuple> search(String rdn, String filter, 
    SearchControls searchControls) throws NamingException {
    String searchBase = getSearchBase(rdn);
    LOGGER.fine(() -> {
      return String.format("Searching %s with filter %s", searchBase,
        filter);
    });

    NamingEnumeration<SearchResult> result = ldapContext.search(
        searchBase, filter, searchControls);
    List<StringTuple> searchResults = loadResults(result);
    Collections.sort(searchResults);
    return searchResults;
  }

  private String getSearchBase(String rdn) {
    StringJoiner searchBase = new StringJoiner(",");
    return searchBase.add(rdn).add(baseDn).toString();
  }

  private List<StringTuple> loadResults(NamingEnumeration<SearchResult> result) 
    throws NamingException {
    List<StringTuple> attributes = new ArrayList<>();
    while (result.hasMore()) {
      try {
        SearchResult sr = result.next();
        Attributes attrs = sr.getAttributes();
        NamingEnumeration<String> attrNames = attrs.getIDs();
        while (attrNames.hasMore()) {
          String name = attrNames.next();
          attributes.addAll(attrsToTuples(attrs, name));
        }
      } catch (PartialResultException e) {
        LOGGER.log(Level.FINE, e, () -> {
          return String.format("Ignoring %s", e.getClass().getName());
        });
      }
    }
    return attributes;
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