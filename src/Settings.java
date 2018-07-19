import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Map;
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
    KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
    ks.load(null, PASSWORD);

    SecretKey key = new SecretKeySpec(secret, KEY_ALGORITHM);
    KeyStore.SecretKeyEntry ske = new KeyStore.SecretKeyEntry(key);
    KeyStore.PasswordProtection kp = new KeyStore.PasswordProtection(PASSWORD);
    ks.setEntry(name, ske, kp);

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
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
      KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
      ByteArrayInputStream bis = new ByteArrayInputStream(secretBytes);
      ks.load(bis, PASSWORD);
      SecretKey key = (SecretKey) ks.getKey(name, PASSWORD);
      byte[] keybytes = key.getEncoded();
      secretText = new char[keybytes.length];
      for (int i = 0; i < keybytes.length; i++) {
        secretText[i] = (char) (0x00ff & keybytes[i]);
      }
    }
    return secretText;
  }

  static Preferences getConnectionSettings(String name) {
    String prefnodeName = String.format("%s/%s", CONNECTION_PREFS_ROOT_NODE_NAME, name);
    return Preferences.userRoot().node(prefnodeName);
  }

  static Map<String, Object> getConnectionSettingsAsMap(String name) {
    Preferences connectionSettingsPref = getConnectionSettings(name);
    String ldapUrl = connectionSettingsPref.get(Settings.LDAP_URL_SETTING, "");
    String host = ldapUrl.isEmpty() ? "" : URI.create(ldapUrl).getHost();

    return Map.of("name", name, "host", host, "ldapUrl", ldapUrl, "baseDn",
        connectionSettingsPref.get(Settings.BASE_DN_SETTING, ""), "authType",
        connectionSettingsPref.get(Settings.AUTHTYPE_SETTING, ""), "bindDn",
        connectionSettingsPref.get(Settings.BIND_DN_SETTING, ""), "useStartTls",
        connectionSettingsPref.getBoolean(Settings.USE_STARTTLS_SETTING, false));
  }

  static String getActiveConnectionName() {
    return Preferences.userRoot().node(PREFERENCES_ROOT_NODE_NAME).get(ACTIVE_CONN_NAME_SETTING, "");
  }

  static byte[] exportConnectionSettings(String name) throws IOException, BackingStoreException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    String prefnodeName = String.format("%s/%s", CONNECTION_PREFS_ROOT_NODE_NAME, name);
    Preferences settings = Preferences.userRoot().node(prefnodeName);
    settings.exportSubtree(bos);
    LOGGER.fine(() -> {
      return String.format("Exported %s connection settings.", name);
    });
    return bos.toByteArray();
  }

  static byte[] exportAllConnectionSettings() throws IOException, BackingStoreException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    Preferences settings = Preferences.userRoot().node(CONNECTION_PREFS_ROOT_NODE_NAME);
    settings.exportSubtree(bos);
    LOGGER.fine("Exported all connection settings.");
    return bos.toByteArray();
  }

  static void importSettings(byte[] settings) throws IOException, InvalidPreferencesFormatException {
    importSettings(new ByteArrayInputStream(settings));
  }

  static void importSettings(InputStream is) throws IOException, InvalidPreferencesFormatException {
    Preferences.importPreferences(is);
    LOGGER.fine("Import settings complete.");
  }
}
