final class StringTuple implements Comparable<StringTuple> {

  final String s1;

  final String s2;

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

  @Override
  public String toString() {
    return new StringBuilder(StringTuple.class.getName())
      .append("[s1=").append(s1).append(",s2=").append(s2)
      .append("]").toString();
  }
}