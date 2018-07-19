import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

final class Maps {

  private Maps() {
    // Empty constructor prevents instantiation.
  }

  static Map<String, Object> toMap(String dn, Attributes attributes) throws NamingException {
    List<Map<String, Object>> attributeMaps = new ArrayList<>();
    NamingEnumeration<? extends Attribute> attributeEnumeration = attributes.getAll();
    while (attributeEnumeration.hasMore()) {
      attributeMaps.add(toMap(attributeEnumeration.next()));
    }
    return Map.of("dn", dn, "attributes", attributeMaps);
  }

  static Map<String, Object> toMap(Attribute attribute) throws NamingException {
    Map<String, Object> map;
    if (attribute.size() > 1) {
      List<Object> values = new ArrayList<>();
      for (int i = 0; i < attribute.size(); i++) {
        values.add(attribute.get(i));
      }
      map = Map.of("name", attribute.getID(), "value", values);
    } else {
      map = Map.of("name", attribute.getID(), "value", attribute.get(0));
    }
    return map;
  }
}
