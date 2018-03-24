import java.io.IOException;
import java.net.URI;
import java.util.Formatter;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

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
}