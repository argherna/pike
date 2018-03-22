import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
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

  private static final char[] PASSWORD = 
    new char[] {'\u0073', '\u0065', '\u0063', '\u0072', '\u0065', '\u0074'};
  // ↑ ↑ ↑ ↑ HA HA! Not so much, amirite???? ↑ ↑ ↑ ↑ 
    
  private static final String KEYSTORE_TYPE = "PKCS12";

  private static final String KEY_ALGORITHM = "AES";

  static final String PREFERENCES_ROOT_NODE_NAME = "/pike";

  static final String CONNECTION_PREFS_ROOT_NODE_NAME = 
    String.format("%s/connections", PREFERENCES_ROOT_NODE_NAME);

  static final String BASE_DN_SETTING = "baseDn";
    
  static final String BIND_DN_SETTING = "bindDn";
    
  static final String LDAP_URL_SETTING = "ldap-url";
  
  static final String PASSWORD_SETTING = "password-store-bytes";

  static final String USE_STARTTLS_SETTING = "use-starttls";


  private Settings() {
    // Empty constructor prevents instantiation.
  }

  /**
   * Saves the given settings and returns them as Preferences.
   * 
   * <p>
   * The returned preferences object has the following key-value pairs:
   * <dl>
   * <dt>{@value #LDAP_URL_SETTING}
   * <dd>Url for the LDAP server
   * <dt>{@value #BIND_DN_SETTING}
   * <dd>LDAP bind DN for the user who owns the connection
   * <dt>{@value #PASSWORD_SETTING}
   * <dd>password for the bind DN
   * <dt>{@value #USE_STARTTLS_SETTING}
   * <dd>flag to indicate that the connection needs to be made over Start TLS
   * </dl>
   * 
   * <p>
   * The password is encrypted before it's saved as a preference. It will be 
   * serialized to a {@value #KEYSTORE_TYPE} KeyStore.
   * 
   * @param name the name for the Preferences.
   * @param ldapUrl Url for the LDAP server
   * @param bindDn LDAP bind DN for the user who owns the connection
   * @param password password for the bind DN
   * @param useStartTls flag to indicate that the connection needs to be made
   *   over Start TLS.
   * @return the Preferences object that has been saved to the backing store.
   * @throws BackingStoreException
   * @throws CertificateException
   * @throws IOException
   * @throws KeyStoreException
   * @throws NoSuchAlgorithmException
   */
  static Preferences saveConnectionSettings(String name, String ldapUrl, 
    String baseDn, String bindDn, String password, boolean useStartTls) 
    throws BackingStoreException, KeyStoreException, IOException, 
    NoSuchAlgorithmException, CertificateException {
    String prefnodeName = String.format("%s/%s",  
      CONNECTION_PREFS_ROOT_NODE_NAME, name);
    Preferences connectionPrefs = Preferences.userRoot().node(prefnodeName);
    connectionPrefs.put(LDAP_URL_SETTING, ldapUrl);
    connectionPrefs.put(BASE_DN_SETTING, baseDn);
    connectionPrefs.put(BIND_DN_SETTING, bindDn);
    // Could be the source of any of the exceptions listed as thrown except
    // for BackingStoreException.
    connectionPrefs.putByteArray(PASSWORD_SETTING, secretToByteArray(bindDn, 
      password.getBytes()));
    connectionPrefs.putBoolean(USE_STARTTLS_SETTING, useStartTls);
    // These 2 could throw a BackingStoreException.
    connectionPrefs.flush();
    connectionPrefs.sync();
    LOGGER.info(() -> {
      return String.format(
        "Saved %s settings: %s=%s,%s=%s,%s=%s,%s=********,%s=%b", name, 
        LDAP_URL_SETTING, ldapUrl, BASE_DN_SETTING, baseDn, BIND_DN_SETTING, 
        bindDn, PASSWORD_SETTING, USE_STARTTLS_SETTING, useStartTls);
    });
    return connectionPrefs;
  }

  /**
   * Stores the secret in a keystore that's serialized to a byte array.
   * 
   * @param name alias for the key
   * @param secret the secret to store in a KeyStore
   * @return byte array of the KeyStore
   * @throws KeyStoreException
   * @throws IOException
   * @throws NoSuchAlgorithmException
   * @throws CertificateException
   */
  static byte[] secretToByteArray(String name, byte[] secret) 
    throws KeyStoreException, IOException, NoSuchAlgorithmException,
    CertificateException {
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
   * @param name the alias for the key
   * @param secretBytes the KeyStore as a byte array
   * @return a char array of the password
   * @throws CertificateException
   * @throws IOException
   * @throws KeyStoreException
   * @throws NoSuchAlgorithmException
   * @throws UnrecoverableKeyException
   */
  static char[] byteArrayToSecretText(String name, byte[] secretBytes) 
    throws IOException, NoSuchAlgorithmException, CertificateException,
    KeyStoreException, UnrecoverableKeyException {
    char[] secretText = null;

    KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
    ByteArrayInputStream bis = new ByteArrayInputStream(secretBytes);
    ks.load(bis, PASSWORD);
    SecretKey key = (SecretKey) ks.getKey(name, PASSWORD);
    byte[] keybytes = key.getEncoded();
    secretText = new char[keybytes.length];
    for (int i = 0; i < keybytes.length; i++) {
      secretText[i] = (char)(0x00ff & keybytes[i]);
    }
    return secretText;
  }

  static Preferences getAllConnectionSettings() {
    return Preferences.userRoot().node(CONNECTION_PREFS_ROOT_NODE_NAME);
  }

  static Preferences getConnectionSettings(String name) {
    String prefnodeName = String.format("%s/%s", 
      CONNECTION_PREFS_ROOT_NODE_NAME, name);
    return Preferences.userRoot().node(prefnodeName);
  }

  static byte[] exportConnectionSettings(String name) throws IOException,
    BackingStoreException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    String prefnodeName = String.format("/pike/connections/%s", name);
    Preferences settings = Preferences.userRoot().node(prefnodeName);
    settings.exportNode(bos);
    LOGGER.info(() -> {
      return String.format("Exported %s connection settings.", name);
    });
    return bos.toByteArray();
  }

  static byte[] exportAllConnectionSettings() throws IOException,
    BackingStoreException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    Preferences settings = Preferences.userRoot().node(
      CONNECTION_PREFS_ROOT_NODE_NAME);
    settings.exportSubtree(bos);
    LOGGER.info("Exported all connection settings.");
    return bos.toByteArray();
  }

  static void importSettings(byte[] settings) throws IOException, 
    InvalidPreferencesFormatException {
    ByteArrayInputStream bis = new ByteArrayInputStream(settings);
    Preferences.importPreferences(bis);
    LOGGER.info("Import settings complete.");
  }

  public static void main(String... args) {
    String name = "sample-dev";
    String ldapUrl = "ldap://localhost";
    String baseDn = "dc=example,dc=com";
    String bindDn = "cn=foo";
    String password = "changeit";
    boolean useStartTls = true;
    String pass = "\u001B[32m\u2713\u001B[0m";
    String fail = "\u001B[31m\u2717\u001B[0m";

    try {
      Preferences settings = Settings.getConnectionSettings(name);
      System.err.print(">>>> Verifying children are empty before save... ");
      String[] keys = settings.keys();
      System.err.println(keys.length == 0 ? pass: fail);

      System.err.print(">>>> Verifying save... ");
      Preferences saved = null;
      try {
        saved = Settings.saveConnectionSettings(name, ldapUrl, 
          baseDn, bindDn, password, useStartTls);
        keys = saved.keys();
        System.err.println(keys.length > 0 ? pass : fail);
      } catch (Exception e) {
        System.err.println(fail + " " + e);
        throw e;
      }

      System.err.println(">>>> Verifying saved values...");
      System.err.println("  ++ " + Settings.LDAP_URL_SETTING + " " + 
        (saved.get(Settings.LDAP_URL_SETTING, "not set").equals(ldapUrl) ? 
          pass : fail));
      System.err.println("  ++ " + Settings.BASE_DN_SETTING + " " + 
        (saved.get(Settings.BASE_DN_SETTING, "not set").equals(baseDn) ? 
          pass : fail));
      System.err.println("  ++ " + Settings.BIND_DN_SETTING + " " + 
        (saved.get(Settings.BIND_DN_SETTING, "not set").equals(bindDn) ? 
          pass : fail));
          System.err.println("  ++ " + Settings.USE_STARTTLS_SETTING + " " + 
          (saved.getBoolean(Settings.USE_STARTTLS_SETTING, !useStartTls) == useStartTls ? 
          pass : fail));
      System.err.println("  ++ " + Settings.PASSWORD_SETTING + " " + 
        (new String(byteArrayToSecretText(bindDn, 
          saved.getByteArray(Settings.PASSWORD_SETTING, new byte[0]))).equals(password) ? 
          pass : fail));
      saved.removeNode();
      saved.flush();
      Preferences pike = Preferences.userRoot().node("/pike");
      pike.removeNode();
      pike.flush();
      pike.sync();
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
  }
}