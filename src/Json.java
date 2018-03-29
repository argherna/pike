import java.io.IOException;
import java.net.URI;
import java.util.Formatter;
import java.util.List;
import java.util.StringJoiner;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchResult;

final class Json {

  private Json() {
    // Empty constructor prevents instantiation.
  }

  static String renderConnections(Preferences connectionSettings) 
    throws IOException {
    try (Formatter f = new Formatter()) {
      f.out().append("[");
      String[] connectionNames = connectionSettings.childrenNames();

      for (int i = 0; i < connectionNames.length; i++) {
        Preferences connection = Settings.getConnectionSettings(
          connectionNames[i]);
        String ldapUrl = connection.get(Settings.LDAP_URL_SETTING, "");
        String host = ldapUrl.isEmpty() ? "" : URI.create(ldapUrl).getHost();
        f.out().append(" {");
        f.format("\"name\" : \"%s\", ", connectionNames[i]);
        f.format("\"host\" : \"%s\", ", host);
        f.format("\"bindDn\" : \"%s\"", connection.get(
          Settings.BIND_DN_SETTING, ""));
        f.out().append("}");
        if (i < connectionNames.length - 1) {
          f.out().append(", ");
        }
      }
      f.out().append(" ]");
      return f.toString();
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  static String renderConnection(String name, 
    Preferences connection) throws Exception {
    try (Formatter f = new Formatter()) {
      f.out().append('{');
      f.format("\"name\": \"%s\",", name);
      f.format("\"ldapUrl\": \"%s\",", 
        connection.get(Settings.LDAP_URL_SETTING, ""));
      f.format("\"baseDn\": \"%s\",", 
        connection.get(Settings.BASE_DN_SETTING, ""));
      String bindDn = connection.get(Settings.BIND_DN_SETTING, "");
      f.format("\"bindDn\": \"%s\",", bindDn);
      f.format("\"useStartTls\": %b", 
        connection.getBoolean(Settings.USE_STARTTLS_SETTING, false));
      f.out().append('}');
      return f.toString();
    }
  }

  static String renderSearch(String hostname, String bindDn, String rdn, 
    String filter, List<String> attrs, String scope, 
    NamingEnumeration<SearchResult> results) throws NamingException, 
    IOException {
      
    try (Formatter f = new Formatter()) {
      f.out().append('{');
      f.format("\"connection\":{\"host\":\"%s\",\"bindDn\":\"%s\"}", hostname, 
        bindDn);

      if (!Strings.isNullOrEmpty(rdn) || !Strings.isNullOrEmpty(filter) ||
        (attrs != null && !attrs.isEmpty()) || !Strings.isNullOrEmpty(scope)) {
        StringJoiner sj = new StringJoiner(",");
        if (!Strings.isNullOrEmpty(rdn)) {
          sj.add("\"rdn\":\"" + rdn + "\"");
        }
        if (!Strings.isNullOrEmpty(filter)) {
          sj.add("\"filter\":\"" + filter + "\"");
        }
        if (attrs != null && !attrs.isEmpty()) {
          StringJoiner sjAttrs = new StringJoiner(",");
          attrs.stream().forEach(attr -> sjAttrs.add("\"" + attr + "\""));
          sj.add("\"attrs\":[" + sjAttrs.toString() + "]");
        }
        if (!Strings.isNullOrEmpty(scope)) {
          sj.add("\"searchScope\":\"" + scope + "\"");
        }
        f.out().append(",\"parameters\":{").append(sj.toString()).append('}');
      }
      
      f.out().append('}');
      return f.toString();
    }
  }
}