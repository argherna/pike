import java.io.IOException;
import java.util.Formatter;

final class Html {

  private Html() {
    // Empty constructor prevents instantiation.
  }
  
  static String renderError(HttpStatus status, String text) 
    throws IOException {
    return renderError(status, text, null, null);
  }

  static String renderError(HttpStatus status, String text, String hostname,
    String authentication) throws IOException {

    /* 
     * Use a single Formatter object when rendering a page which is better for
     * memory usage. Each call to String.format creates a new Formatter object
     * on the heap and depending on how many different elements go into a page
     * this could be costly over time.
     */ 

    try (Formatter f = new Formatter(startRender())) {
      f.format(IO.loadUtf8ResourceFromClasspath("templates/error-head.html"), 
        status.getStatusCode(), status.getMessage());
      headToBody(f.out());
      
      f.format(IO.loadUtf8ResourceFromClasspath(
        "templates/error-body.html"), status.getMessage(), text);
      
      renderFooter(f, hostname, authentication);
      endRender(f.out());
      return f.toString();
    }
  }
  
  private static Appendable startRender() throws IOException {
    // There aren't any parameters in the opening boilerplate, so 
    // start with it in a StringBuilder to give to a Formatter.
    return new StringBuilder(
      IO.loadUtf8ResourceFromClasspath("templates/basedoc-open-bp.html"));
  }

  private static void headToBody(Appendable buffer) throws IOException {
    // There aren't any parameters in this boilerplate, so append it to
    // the given buffer that (hopefully) came from a Formatter.
    buffer.append(IO.loadUtf8ResourceFromClasspath(
      "templates/basedoc-head-to-body-bp.html"));
  }

  private static void endRender(Appendable buffer) throws IOException {
    // Append boilerplate to the end of the document.
    buffer.append(IO.loadUtf8ResourceFromClasspath(
        "templates/basedoc-close-bp.html"));
  }
  
  private static Formatter renderFooter(Formatter f, String hostname, String authentication)
    throws IOException {
    if (!Strings.isNullOrEmpty(hostname) && 
      !Strings.isNullOrEmpty(authentication)) {
      return f.format(IO.loadUtf8ResourceFromClasspath(
        "templates/basedoc-footer.html"), hostname, authentication);
    } else {
      return f;
    }
  }
}
