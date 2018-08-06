package com.github.argherna.pike;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Manage the settings for pike.
 * 
 * @see Preferences
 */
final class Settings {

  private static final Logger LOGGER = Logger.getLogger(Settings.class.getName());

  private static final char[] PASSWORD = new char[] { '\u0073', '\u0065', '\u0063', '\u0072', '\u0065', '\u0074' };
  // ↑ ↑ ↑ ↑ HA HA! Not so much, amirite???? ↑ ↑ ↑ ↑

  private static final String KEYSTORE_TYPE = "PKCS12";

  private static final String KEY_ALGORITHM = "AES";

  static final String PREFERENCES_ROOT_NODE_NAME = "/pike";

  static final String CONNECTION_PREFS_ROOT_NODE_NAME = String.format("%s/connections", PREFERENCES_ROOT_NODE_NAME);

  static final String ACTIVE_CONN_NAME_SETTING = "active-connection-name";

  static final String BASE_DN_SETTING = "baseDn";

  static final String BIND_DN_SETTING = "bindDn";

  static final String LDAP_URL_SETTING = "ldap-url";

  static final String PASSWORD_SETTING = "password-store-bytes";

  static final String USE_STARTTLS_SETTING = "use-starttls";

  static final String AUTHTYPE_SETTING = "auth-type";

  static final String REFERRAL_POLICY_SETTING = "referral-policy";

  static final String RDN_SETTING = "rdn";

  static final String FILTER_SETTING = "filter";

  static final String ATTRS_TO_RETURN_SETTING = "attrs-to-return";

  static final String SCOPE_SETTING = "scope";

  private Settings() {
    // Empty constructor prevents instantiation.
  }

  /**
   * Stores the secret in a keystore that's serialized to a byte array.
   * 
   * @param name   alias for the key
   * @param secret the secret to store in a KeyStore
   * @return byte array of the KeyStore
   * @throws KeyStoreException
   * @throws IOException
   * @throws NoSuchAlgorithmException
   * @throws CertificateException
   */
  static byte[] secretToByteArray(String name, byte[] secret)
      throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
    var ks = KeyStore.getInstance(KEYSTORE_TYPE);
    ks.load(null, PASSWORD);

    var key = new SecretKeySpec(secret, KEY_ALGORITHM);
    var ske = new KeyStore.SecretKeyEntry(key);
    var kp = new KeyStore.PasswordProtection(PASSWORD);
    ks.setEntry(name, ske, kp);

    var bos = new ByteArrayOutputStream();
    ks.store(bos, PASSWORD);

    return bos.toByteArray();
  }

  /**
   * Extracts the password from the the KeyStore byte array.
   * 
   * @param name        the alias for the key
   * @param secretBytes the KeyStore as a byte array
   * @return a char array of the password; empty if there is none.
   * @throws CertificateException
   * @throws IOException
   * @throws KeyStoreException
   * @throws NoSuchAlgorithmException
   * @throws UnrecoverableKeyException
   */
  static char[] byteArrayToSecretText(String name, byte[] secretBytes)
      throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException, UnrecoverableKeyException {
    char[] secretText = new char[0];

    if (secretBytes.length > 0) {
      var ks = KeyStore.getInstance(KEYSTORE_TYPE);
      var bis = new ByteArrayInputStream(secretBytes);
      ks.load(bis, PASSWORD);
      var key = (SecretKey) ks.getKey(name, PASSWORD);
      var keybytes = key.getEncoded();
      secretText = new char[keybytes.length];
      for (int i = 0; i < keybytes.length; i++) {
        secretText[i] = (char) (0x00ff & keybytes[i]);
      }
    }
    return secretText;
  }

  static String[] getAllConnectionNames() {
    try {
      return Preferences.userRoot().node(CONNECTION_PREFS_ROOT_NODE_NAME).childrenNames();
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  static String[] getSearchNames(String connectionName) {
    try {
      return Preferences.userRoot().node(CONNECTION_PREFS_ROOT_NODE_NAME + "/" + connectionName + "/searches")
          .childrenNames();
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  static Settings.ConnectionSettings getConnectionSettings(String name) {
    var prefs = Preferences.userRoot().node(String.format("%s/%s", CONNECTION_PREFS_ROOT_NODE_NAME, name));
    return new Settings.ConnectionSettings.Builder(name).authType(prefs.get(AUTHTYPE_SETTING, ""))
        .baseDn(prefs.get(BASE_DN_SETTING, "")).bindDn(prefs.get(BIND_DN_SETTING, ""))
        .ldapUrl(prefs.get(LDAP_URL_SETTING, "")).password(prefs.getByteArray(PASSWORD_SETTING, new byte[0]))
        .referralPolicy(prefs.get(REFERRAL_POLICY_SETTING, ""))
        .useStartTls(prefs.getBoolean(USE_STARTTLS_SETTING, false)).build();
  }

  static Settings.SearchSettings getSearchSettings(String connectionName, String searchName) {
    var prefs = Preferences.userRoot()
        .node(CONNECTION_PREFS_ROOT_NODE_NAME + "/" + connectionName + "/searches/" + searchName);
    return new Settings.SearchSettings.Builder(searchName).attrsToReturn(prefs.get(ATTRS_TO_RETURN_SETTING, ""))
        .filter(prefs.get(FILTER_SETTING, "")).rdn(prefs.get(RDN_SETTING, "")).scope(prefs.get(SCOPE_SETTING, ""))
        .build();
  }

  static String getActiveConnectionName() {
    return Preferences.userRoot().node(PREFERENCES_ROOT_NODE_NAME).get(ACTIVE_CONN_NAME_SETTING, "");
  }

  static void setActiveConnectionName(String name) {
    try {
      var activeConnectionName = Preferences.userRoot().node(PREFERENCES_ROOT_NODE_NAME);
      activeConnectionName.put(ACTIVE_CONN_NAME_SETTING, name);
      activeConnectionName.flush();
      activeConnectionName.sync();
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  static void unsetActiveConnectionName() {
    var activeConnectionName = Preferences.userRoot().node(PREFERENCES_ROOT_NODE_NAME);
    try {
      activeConnectionName.remove(ACTIVE_CONN_NAME_SETTING);
      activeConnectionName.flush();
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  static void deleteSingleConnection(String name) {
    try {
      var connectionToDelete = Preferences.userRoot()
          .node(String.format("%s/%s", CONNECTION_PREFS_ROOT_NODE_NAME, name));
      connectionToDelete.removeNode();
      connectionToDelete.flush();
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  static void deleteAllConnections() {
    try {
      var connections = Preferences.userRoot().node(CONNECTION_PREFS_ROOT_NODE_NAME);
      connections.removeNode();
      connections.flush();
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  static void deleteSingleSearch(String connectionName, String searchName) {
    var search = Preferences.userRoot()
        .node(CONNECTION_PREFS_ROOT_NODE_NAME + "/" + connectionName + "/searches/" + searchName);
    try {
      search.removeNode();
      search.flush();
      LOGGER.fine(String.format("Deleted saved search %s from %s.", searchName, connectionName));
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  static boolean savedSearchExists(String connectionName, String searchName) {
    try {
      return Preferences.userRoot()
          .nodeExists(CONNECTION_PREFS_ROOT_NODE_NAME + "/" + connectionName + "/searches/" + searchName);
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  static byte[] exportConnectionSettings(String name) {
    try {
      var bos = new ByteArrayOutputStream();
      var prefnodeName = String.format("%s/%s", CONNECTION_PREFS_ROOT_NODE_NAME, name);
      var settings = Preferences.userRoot().node(prefnodeName);
      settings.exportSubtree(bos);
      LOGGER.fine(() -> String.format("Exported %s connection settings.", name));
      return bos.toByteArray();
    } catch (IOException | BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  static byte[] exportAllConnectionSettings() {
    try {
      var bos = new ByteArrayOutputStream();
      var settings = Preferences.userRoot().node(CONNECTION_PREFS_ROOT_NODE_NAME);
      settings.exportSubtree(bos);
      LOGGER.fine("Exported all connection settings.");
      return bos.toByteArray();
    } catch (IOException | BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  static void importSettings(byte[] settings) {
    importSettings(new ByteArrayInputStream(settings));
  }

  static void importSettings(InputStream is) {
    try {
      Preferences.importPreferences(is);
      LOGGER.fine("Import settings complete.");
    } catch (IOException | InvalidPreferencesFormatException e) {
      throw new RuntimeException(e);
    }
  }

  static void saveConnectionSettings(Settings.ConnectionSettings connSettings) throws Exception {
    var connection = Preferences.userRoot()
        .node(String.format("%s/%s", CONNECTION_PREFS_ROOT_NODE_NAME, connSettings.getName()));
    if (Strings.nullToEmpty(connSettings.getLdapUrl()).length() > 0) {
      connection.put(LDAP_URL_SETTING, connSettings.getLdapUrl());
    }
    if (Strings.nullToEmpty(connSettings.getBaseDn()).length() > 0) {
      connection.put(BASE_DN_SETTING, connSettings.getBaseDn());
    }
    if (Strings.nullToEmpty(connSettings.getBindDn()).length() > 0) {
      connection.put(BIND_DN_SETTING, connSettings.getBindDn());
    }
    if (connSettings.getPassword().length > 0) {
      connection.putByteArray(PASSWORD_SETTING,
          secretToByteArray(connSettings.getBindDn(), connSettings.getPassword()));
    }
    connection.putBoolean(USE_STARTTLS_SETTING, connSettings.getUseStartTls());
    if (connSettings.getAuthType().length() > 0) {
      connection.put(AUTHTYPE_SETTING, connSettings.getAuthType());
    }
    if (Strings.nullToEmpty(connSettings.getReferralPolicy()).length() > 0) {
      connection.put(REFERRAL_POLICY_SETTING, connSettings.getReferralPolicy());
    }
    connection.flush();
    connection.sync();
    LOGGER.fine(() -> String.format("Saved %s connection settings.", connSettings.getName()));
  }

  static void saveSearchSettings(String connectionName, Settings.SearchSettings searchSettings) throws Exception {
    var search = Preferences.userRoot()
        .node(CONNECTION_PREFS_ROOT_NODE_NAME + "/" + connectionName + "/searches/" + searchSettings.getName());
    if (Strings.nullToEmpty(searchSettings.getFilter()).length() > 0) {
      search.put(FILTER_SETTING, searchSettings.getFilter());
    }
    if (Strings.nullToEmpty(searchSettings.getRdn()).length() > 0) {
      search.put(RDN_SETTING, searchSettings.getRdn());
    }
    if (Strings.nullToEmpty(searchSettings.getScope()).length() > 0) {
      search.put(SCOPE_SETTING, searchSettings.getScope());
    }
    if (!searchSettings.getAttrsToReturn().isEmpty()) {
      var sj = new StringJoiner(",");
      searchSettings.getAttrsToReturn().stream().forEach(s -> sj.add(s));
      search.put(ATTRS_TO_RETURN_SETTING, sj.toString());
    }
    search.flush();
    search.sync();
    LOGGER.fine(
        () -> String.format("Saved %s search settings for %s connection.", searchSettings.getName(), connectionName));
  }

  /**
   * Connection settings encapsulated in a struct-type Object.
   */
  static final class ConnectionSettings {

    private final String name;

    private final String ldapUrl;

    private final String baseDn;

    private final String authType;

    private final String bindDn;

    private final boolean useStartTls;

    private final String referralPolicy;

    private final byte[] password;

    static final class Builder {

      private String name;

      private String ldapUrl;

      private String baseDn;

      private String authType;

      private String bindDn;

      private boolean useStartTls = false;

      private String referralPolicy;

      private byte[] password = new byte[0];

      Builder(String name) {
        this.name = Objects.requireNonNull(name, "Connection name cannot be null");
      }

      Builder ldapUrl(String ldapUrl) {
        this.ldapUrl = Objects.requireNonNull(ldapUrl, "LDAP Url cannot be null");
        return this;
      }

      Builder baseDn(String baseDn) {
        this.baseDn = baseDn;
        return this;
      }

      Builder authType(String authType) {
        this.authType = Objects.requireNonNull(authType, "Authtype cannot be null");
        return this;
      }

      Builder bindDn(String bindDn) {
        this.bindDn = bindDn;
        return this;
      }

      Builder useStartTls(boolean useStartTls) {
        this.useStartTls = useStartTls;
        return this;
      }

      Builder referralPolicy(String referralPolicy) {
        this.referralPolicy = referralPolicy;
        return this;
      }

      Builder password(byte[] password) {
        this.password = password;
        return this;
      }

      ConnectionSettings build() {
        return new ConnectionSettings(this);
      }
    }

    private ConnectionSettings(Builder builder) {
      this.name = builder.name;
      this.ldapUrl = builder.ldapUrl;
      this.baseDn = builder.baseDn;
      this.authType = builder.authType;
      this.bindDn = builder.bindDn;
      this.useStartTls = builder.useStartTls;
      this.referralPolicy = builder.referralPolicy;
      this.password = builder.password;
    }

    String getName() {
      return name;
    }

    String getLdapUrl() {
      return ldapUrl;
    }

    String getBaseDn() {
      return baseDn;
    }

    String getAuthType() {
      return authType;
    }

    String getBindDn() {
      return bindDn;
    }

    boolean getUseStartTls() {
      return useStartTls;
    }

    String getReferralPolicy() {
      return referralPolicy;
    }

    byte[] getPassword() {
      byte[] copy = new byte[password.length];
      System.arraycopy(password, 0, copy, 0, password.length);
      return copy;
    }
  }

  /**
   * Search settings encapsulated in a struct-type Object.
   */
  static final class SearchSettings {

    private final String name;

    private final String rdn;

    private final String filter;

    private final String scope;

    private final List<String> attrsToReturn;

    static final class Builder {

      private final String name;

      private String rdn;

      private String filter;

      private String scope;

      private List<String> attrsToReturn = List.of();

      Builder(String name) {
        this.name = Objects.requireNonNull(name);
      }

      Builder rdn(String rdn) {
        this.rdn = rdn;
        return this;
      }

      Builder filter(String filter) {
        this.filter = filter;
        return this;
      }

      Builder scope(String scope) {
        this.scope = scope;
        return this;
      }

      Builder attrsToReturn(String attrsToReturn) {
        if (attrsToReturn.length() > 0) {
          this.attrsToReturn = List.of(attrsToReturn.split(","));
        }
        return this;
      }

      Builder attrsToReturn(Collection<String> attrsToReturn) {
        this.attrsToReturn = new ArrayList<>();
        this.attrsToReturn.addAll(attrsToReturn);
        return this;
      }

      SearchSettings build() {
        return new SearchSettings(this);
      }
    }

    private SearchSettings(SearchSettings.Builder builder) {
      this.name = builder.name;
      this.rdn = builder.rdn;
      this.filter = builder.filter;
      this.scope = builder.scope;
      this.attrsToReturn = new ArrayList<>();
      this.attrsToReturn.addAll(builder.attrsToReturn);
    }

    String getName() {
      return this.name;
    }

    String getRdn() {
      return rdn;
    }

    String getFilter() {
      return filter;
    }

    String getScope() {
      return scope;
    }

    List<String> getAttrsToReturn() {
      var copy = new ArrayList<String>();
      copy.addAll(this.attrsToReturn);
      return copy;
    }
  }
}
