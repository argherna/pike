final class StringTuple implements Comparable<StringTuple> {

  public final String s1;

  public final String s2;

  StringTuple(String s1, String s2) {
    this.s1 = s1;
    this.s2 = s2;
  }

  @Override
  public int compareTo(StringTuple t) {
    int s1Compare = this.s1.compareTo(t.s1);
    if (s1Compare == 0) {
      return this.s2.compareTo(t.s2);
    } else {
      return s1Compare;
    }
  }
}