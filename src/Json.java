import java.io.IOException;
import java.net.URI;
import java.rmi.Naming;
import java.util.Formatter;
import java.util.List;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;

final class Json {

  private static final Logger LOGGER = Logger.getLogger(Json.class.getName());

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
      f.out().append("\"connection\":")
        .append(renderConnectionInformation(hostname, bindDn));

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
      f.out().append(",\"records\": [");
      if (results != null && results.hasMore()) {
        StringJoiner sj  = new StringJoiner(",");
        while (results.hasMore()) {
          SearchResult result = results.next();
          sj.add(renderRecord(result.getNameInNamespace(), 
            result.getAttributes()));
        }
        f.out().append(sj.toString());
      }
      f.out().append(']');
      f.out().append('}');
      return f.toString();
    }
  }

  static String renderSingleRecord(String hostname, String bindDn, String dn, 
    Attributes attributes) throws NamingException {
    StringBuilder singleRecord = new StringBuilder("{\"connection\":");
    singleRecord.append(renderConnectionInformation(hostname, bindDn))
      .append(",\"record\":").append(renderRecord(dn, attributes))
      .append("}");
    return singleRecord.toString();
  }

  static String renderConnectionInformation(String hostname, String bindDn) {
    StringBuilder connInfo = new StringBuilder();
    connInfo.append("{\"host\":\"").append(hostname).append("\",\"bindDn\":\"")
      .append(bindDn).append("\"}");
    return connInfo.toString();
  }

  static String renderRecord(String dn, Attributes attributes) 
    throws NamingException {
    StringBuilder recordJson = new StringBuilder("{");
    recordJson.append("\"dn\":\"").append(dn).append("\",\"attributes\":[");
    NamingEnumeration<? extends Attribute> attrEnum = attributes.getAll();
    StringJoiner sj = new StringJoiner(",");
    while (attrEnum.hasMore()) {
      sj.add(render(attrEnum.next()));
    }
    recordJson.append(sj.toString());
    recordJson.append("]}");
    return recordJson.toString();
  }

  private static String render(Attribute attribute) throws NamingException {
    boolean isMultivalued = attribute.size() > 1;
    StringBuilder attributeJson = new StringBuilder("{");
    String attrID = attribute.getID();
    LOGGER.finest(String.format("Charater types for %s value(s):", attrID));
    attributeJson.append("\"name\":\"").append(attrID).append("\",\"value\":");
    if (isMultivalued) {
      attributeJson.append('[');
      for (int i = 0; i < attribute.size(); i++) {
        attributeJson.append('"');
        appendAttributeValue(attributeJson, attribute.get(i));
        attributeJson.append('"');
        if (i < (attribute.size() - 1)) {
          attributeJson.append(',');
        }
      }
      attributeJson.append(']');
    } else {
      attributeJson.append('"');
      appendAttributeValue(attributeJson, attribute.get(0));
      attributeJson.append('"');
    }
    attributeJson.append('}');
    return attributeJson.toString();
  }

  private static void appendAttributeValue(StringBuilder buffer, Object value) {
    if (value instanceof String) {
      char[] v = value.toString().replace('\n', ' ').replace('\r', ' ')
        .replace("\\", "\\\\").toCharArray();
      logCharTypes(v);
      char[] decoded = new char[v.length];
      for (int i = 0; i < v.length; i++) {
        decoded[i] = toPrintable(v[i]);
      }
      buffer.append(decoded);
    } else {
      buffer.append(value.getClass().getName());
    }
  }

  private static void logCharTypes(char[] value) {
    if (LOGGER.isLoggable(Level.FINEST)) {
      StringBuilder types = new StringBuilder();
      for (char c : value) {
        int charType = Character.getType(c);
        String charTypeName = "UNKNOWN";
        switch (charType) {
          case 0:
            charTypeName = "Character.UNASSIGNED";
            break;
          case 1:
            charTypeName = "Character.UPPERCASE_LETTER";
            break;
          case 2:
            charTypeName = "Character.LOWERCASE_LETTER";
            break;
          case 3:
            charTypeName = "Character.TITLECASE_LETTER";
            break;
          case 4:
            charTypeName = "Character.MODIFIER_LETTER";
            break;
          case 5:
            charTypeName = "Character.OTHER_LETTER";
            break;
          case 6:
            charTypeName = "Character.NON_SPACING_MARK";
            break;
          case 7:
            charTypeName = "Character.ENCLOSING_MARK";
            break;
          case 8:
            charTypeName = "Character.COMBINING_SPACING_MARK";
            break;
          case 9:
            charTypeName = "Character.DECIMAL_DIGIT_NUMBER";
            break;
          case 10:
            charTypeName = "Character.LETTER_NUMBER";
            break;
          case 11:
            charTypeName = "Character.OTHER_NUMBER";
            break;
          case 12:
            charTypeName = "Character.SPACE_SEPARATOR";
            break;
          case 13:
            charTypeName = "Character.LINE_SEPARATOR";
            break;
          case 14:
            charTypeName = "Character.PARAGRAPH_SEPARATOR";
            break;
          case 15:
            charTypeName = "Character.CONTROL";
            break;
          case 16:
            charTypeName = "Character.FORMAT";
            break;
          case 18:
            charTypeName = "Character.PRIVATE_USE";
            break;
          case 19:
            charTypeName = "Character.SURROGATE";
            break;
          case 20:
            charTypeName = "Character.DASH_PUNCTUATION";
            break;
          case 21:
            charTypeName = "Character.START_PUNCTUATION";
            break;
          case 22:
            charTypeName = "Character.END_PUNCTUATION";
            break;
          case 23:
            charTypeName = "Character.CONNECTOR_PUNCTUATION";
            break;
          case 24:
            charTypeName = "Character.OTHER_PUNCTUATION";
            break;
          case 25:
            charTypeName = "Character.MATH_SYMBOL";
            break;
          case 26:
            charTypeName = "Character.CURRENCY_SYMBOL";
            break;
          case 27:
            charTypeName = "Character.MODIFIER_SYMBOL";
            break;
          case 28:
            charTypeName = "Character.OTHER_SYMBOL";
            break;
          case 29:
            charTypeName = "Character.INITIAL_QUOTE_PUNCTUATION";
            break;
          case 30:
            charTypeName = "Character.FINAL_QUOTE_PUNCTUATION";
            break;
        }
        types.append(c).append(": ").append(charTypeName)
          .append(System.getProperty("line.separator"));
      }
      LOGGER.finest(types.toString());
    }
  }

  private static char toPrintable(char c) {
    int type = Character.getType(c);
    switch (type) {
      case Character.CONTROL:
      case Character.OTHER_SYMBOL:
        return '?';
      default:
        return c;
    }
  }
}