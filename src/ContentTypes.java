import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class ContentTypes {
  static final Map<String, String> TYPES;

  static {
    Map<String, String> types = new HashMap<>();
    types.put("css", "text/css");
    types.put("gif", "image/gif");
    types.put("html", "text/html");
    types.put("jpg", "image/jpeg");
    types.put("js", "application/javascript");
    types.put("png", "image/png");
    types.put("svg", "image/svg+xml");
    types.put("woff", "application/x-font-woff");
    types.put("eot", "application/vnd.ms-fontobject");
    types.put("ttf", "application/octet-stream");
    types.put("otf", "application/octet-stream");
    types.put("form", "application/x-www-form-urlencoded");
    TYPES = Collections.unmodifiableMap(types);
  }
}