import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class ContentTypes {
  static final Map<String, String> TYPES;

  static {
    Map<String, String> types = new HashMap<>();
    types.put("css", "text/css");
    types.put("form", "application/x-www-form-urlencoded");
    types.put("html", "text/html");
    types.put("js", "application/javascript");
    types.put("json", "application/json");
    types.put("map", "application/javascript");
    types.put("upload", "multipart/form-data");
    types.put("xml", "text/xml");
    TYPES = Collections.unmodifiableMap(types);
  }
}