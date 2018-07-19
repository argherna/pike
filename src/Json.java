import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

final class Json {

  private static final Logger LOGGER = Logger.getLogger(Json.class.getName());

  private Json() {
    // Empty constructor prevents instantiation.
  }

  // Values can be rendered if they are non-null, and if String non-empty (length
  // > 0).
  private static final Predicate<? super Object> HAS_VALUE = v -> {
    boolean hasValue = Objects.nonNull(v);
    if (hasValue && v instanceof String) {
      hasValue = v.toString().length() > 0;
    }
    return hasValue;
  };

  private static final Predicate<Entry<String, Object>> ENTRY_HAS_VALUE = e -> {
    boolean hasValue = Objects.nonNull(e.getValue());
    if (hasValue && e.getValue() instanceof String) {
      hasValue = e.getValue().toString().length() > 0;
    }
    return hasValue;
  };

  static String renderObject(Map<String, Object> object) throws IOException {
    StringBuilder buffer = new StringBuilder();
    renderObject(object, buffer);
    return buffer.toString();
  }

  static String renderList(Iterable<? extends Object> values) throws IOException {
    StringBuilder buffer = new StringBuilder();
    renderList(values, buffer);
    return buffer.toString();
  }

  private static void renderObject(Map<String, Object> data, Appendable buffer) throws IOException {
    Map<String, Object> filteredData = data.entrySet().stream().filter(ENTRY_HAS_VALUE)
        .collect(Collectors.toMap(k -> k.getKey(), v -> v.getValue()));
    if (!filteredData.isEmpty()) {
      buffer.append("{");
      for (Iterator<String> keys = filteredData.keySet().iterator(); keys.hasNext();) {
        String key = keys.next();
        buffer.append("\"").append(key).append("\":");
        renderValue(filteredData.get(key), buffer);
        if (keys.hasNext()) {
          buffer.append(",");
        }
      }
      buffer.append("}");
    }
  }

  private static void renderList(Iterable<? extends Object> data, Appendable buffer) throws IOException {
    List<? extends Object> filteredData = StreamSupport.stream(data.spliterator(), false).filter(HAS_VALUE)
        .collect(Collectors.toList());
    if (!filteredData.isEmpty()) {
      buffer.append("[");
      for (Iterator<? extends Object> values = data.iterator(); values.hasNext();) {
        Object value = values.next();
        renderValue(value, buffer);
        if (values.hasNext()) {
          buffer.append(",");
        }
      }
      buffer.append("]");
    }
  }

  @SuppressWarnings("unchecked")
  private static void renderValue(Object value, Appendable buffer) throws IOException {
    if (value instanceof List) {
      renderList((List<Object>) value, buffer);
    } else if (value instanceof Map) {
      renderObject((Map<String, Object>) value, buffer);
    } else {
      renderScalar(value, buffer);
    }
  }

  private static void renderScalar(Object value, Appendable buffer) throws IOException {
    if (value == null) {
      buffer.append("null");
    } else if (value instanceof Number || value instanceof Boolean) {
      buffer.append(value.toString());
    } else {
      // Has to be a String (no other option)
      buffer.append("\"");
      char[] valueChars = value.toString().toCharArray();
      for (int i = 0; i < valueChars.length; i++) {
        char valueChar = valueChars[i];
        // Handle escape characters that MUST be handled according to
        // https://www.json.org/.
        if (valueChar == '\b') {
          buffer.append('\\').append('b');
        } else if (valueChar == '\f') {
          buffer.append('\\').append('f');
        } else if (valueChar == '\n') {
          buffer.append('\\').append('n');
        } else if (valueChar == '\r') {
          buffer.append('\\').append('r');
        } else if (valueChar == '\t') {
          buffer.append('\\').append('t');
        } else if (valueChar == '\"') {
          buffer.append('\\').append('\"');
        } else if (valueChar == '\\') {
          buffer.append('\\').append('\\');
        } else {
          logCharType(valueChar);
          buffer.append(toPrintable(valueChar));
        }
      }
      buffer.append("\"");
    }
  }

  private static void logCharType(char value) {
    // Attribute values aren't always JSON-friendly. When things go wrong,
    // set the LOGGER to FINEST and run the query again looking at the output
    // from this method. Update #toPrintable(char) as appropriate.
    if (LOGGER.isLoggable(Level.FINEST)) {
      StringBuilder types = new StringBuilder();
      int charType = Character.getType(value);
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
      types.append(value).append(": ").append(charTypeName);
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

  static Map<String, Object> marshal(String json) {
    // With great thanks to
    // http://www.adam-bien.com/roller/abien/entry/converting_json_to_map_with
    ScriptEngineManager seManager = new ScriptEngineManager();
    ScriptEngine se = seManager.getEngineByName("javascript");
    LOGGER.fine(() -> String.format("Marshalling %s", json));
    String script = String.format("Java.asJSONCompatible(%s)", json);

    try {
      Object evaluated = se.eval(script);
      @SuppressWarnings("unchecked")
      Map<String, Object> marshalled = (Map<String, Object>) evaluated;
      return marshalled;
    } catch (ScriptException e) {
      throw new RuntimeException(e);
    }
  }
}
