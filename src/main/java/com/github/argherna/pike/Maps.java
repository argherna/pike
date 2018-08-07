package com.github.argherna.pike;

import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

final class Maps {

  private Maps() {
    // Empty constructor prevents instantiation.
  }

  static Map<String, Object> toMap(Settings.ConnectionSettings connSettings) {
    return Map
        .of("name", Strings.nullToEmpty(connSettings.getName()), "ldapUrl",
            Strings.nullToEmpty(connSettings.getLdapUrl()), "host",
            getLdapHost(Strings.nullToEmpty(connSettings.getLdapUrl())), "baseDn",
            Strings.nullToEmpty(connSettings.getBaseDn()), "authType", Strings.nullToEmpty(connSettings.getAuthType()),
            "bindDn", Strings.nullToEmpty(connSettings.getBindDn()), "useStartTls", connSettings.getUseStartTls())
        .entrySet().stream().filter(e -> e.getValue().toString().length() > 0)
        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
  }

  private static String getLdapHost(String ldapUrl) {
    if (Strings.isNullOrEmpty(ldapUrl)) {
      return "unknown";
    }
    return URI.create(ldapUrl).getHost();
  }

  static Map<String, Object> toMap(Settings.SearchSettings searchSettings) {
    return Map
        .of("name", Strings.nullToEmpty(searchSettings.getName()), "rdn", Strings.nullToEmpty(searchSettings.getRdn()),
            "filter", Strings.nullToEmpty(searchSettings.getFilter()), "attrsToReturn",
            searchSettings.getAttrsToReturn(), "scope", Strings.nullToEmpty(searchSettings.getScope()))
        .entrySet().stream().filter(e -> e.getValue().toString().length() > 0)
        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
  }

  static Map<String, Object> toMap(String dn, Attributes attributes) throws NamingException {
    var attributeMaps = new ArrayList<Map<String, Object>>();
    var attributeEnumeration = attributes.getAll();
    while (attributeEnumeration.hasMore()) {
      attributeMaps.add(toMap(attributeEnumeration.next()));
    }
    return Map.of("dn", dn, "attributes", attributeMaps);
  }

  static Map<String, Object> toMap(Attribute attribute) throws NamingException {
    if (attribute.size() > 1) {
      var values = new ArrayList<Object>();
      for (int i = 0; i < attribute.size(); i++) {
        values.add(attribute.get(i));
      }
      return Map.of("name", attribute.getID(), "value", values);
    } else {
      return Map.of("name", attribute.getID(), "value", attribute.get(0));
    }
  }
}
