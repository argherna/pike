package com.github.argherna.pike;

final class Strings {

  private Strings() {
    // Empty constructor prevents instantiation.
  }
  
  static boolean isNullOrEmpty(String s) {
    return s == null || s.isEmpty();
  }

  static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
