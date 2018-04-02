import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

final class Strings {

  @Deprecated
  private static final BiFunction<String, String, StringTuple> TO_STRINGTUPLE = (s1, s2) -> {
    return new StringTuple(s1, s2);
  };

  private Strings() {
    // Empty constructor prevents instantiation.
  }
  
  static boolean isNullOrEmpty(String s) {
    return s == null || s.isEmpty();
  }

  static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  @Deprecated
  static List<StringTuple> zip(List<String> i1, List<String> i2) {

    List<String> shorter = i1.size() > i2.size() ? i1 : i2;
    List<StringTuple> zipped = new ArrayList<>();
    for (int i = 0; i < shorter.size(); i++) {
      zipped.add(TO_STRINGTUPLE.apply(i1.get(i), i2.get(i)));
    }
    return zipped;
  }
}