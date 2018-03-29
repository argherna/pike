import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

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

  static String renderSearch() throws IOException {
    return renderSearch(null, null);
  }

  static String renderSearch(String rawQueryString) 
    throws IOException {
    try (Formatter f = new Formatter()) {
      f.format(IO.loadUtf8ResourceFromClasspath("templates/search.html"), rawQueryString);
      return f.toString();
    }
  }

  static String renderSearch(String hostname, String authentication)
    throws IOException {
    return renderSearch(null, null, null, hostname, authentication);
  }

  static String renderSearch(String rdn, String filter, String attrs, 
    String hostname, String authentication) throws IOException {
    try (Formatter f = new Formatter(startRender())) {
      f.out().append("<title>Welcome To Pike!</title>");
      
      headToBody(f.out());
      renderSearchForm(f, rdn, filter, attrs);

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

  private static Formatter renderSearchForm(Formatter f, String rdn, String filter, 
    String attrs) throws IOException {
    return f.format(IO.loadUtf8ResourceFromClasspath(
      "templates/search-form.html"), Strings.nullToEmpty(rdn), 
      Strings.nullToEmpty(filter), Strings.nullToEmpty(attrs));
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

  static String searchForm(String hostname, String authentication) 
    throws IOException {
    String searchFormTemplate = IO.loadUtf8ResourceFromClasspath(
      "templates/search-form.html");
    String searchForm = String.format(searchFormTemplate, "", "", "");

    String footer = "";
    if (hostname != null && authentication != null) {
      String basedocFooterTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/basedoc-footer.html");
      footer = String.format(basedocFooterTemplate, hostname, 
        authentication);
    }
    String basedocTemplate = IO.loadUtf8ResourceFromClasspath(
      "templates/basedoc.html");
    return String.format(basedocTemplate, "<title>Welcome to Pike!</title>", 
      searchForm, footer);
  }
  
  static String resultsView(String filter, 
    Map<String, Collection<StringTuple>> results, String hostname, 
    String authentication, String rdn, String attrs) throws IOException {
    String recordRowTemplate = IO.loadUtf8ResourceFromClasspath(
      "templates/record-table-row.html");
    String tableTemplate = IO.loadUtf8ResourceFromClasspath(
      "templates/record-table.html");

    // Render a table for every DN in the result
    StringBuilder recordTableBuilder = new StringBuilder();
    for (String dn : results.keySet()) {
      Collection<StringTuple> data = results.get(dn);
      StringBuilder rows = new StringBuilder();
      for (StringTuple datum : data) {
        rows.append(String.format(recordRowTemplate, datum.s1, datum.s2))
          .append("\n");
      }
      
      recordTableBuilder.append(String.format(
        tableTemplate, dn, rows.toString()));
    }
      
    String recordHeadTemplate = IO.loadUtf8ResourceFromClasspath(
      "templates/record-head.html");
    String recordHead = String.format(recordHeadTemplate, filter);
    
    String searchFormTemplate = IO.loadUtf8ResourceFromClasspath(
      "templates/search-form.html");
    
    String rdnValue = Strings.isNullOrEmpty(rdn) ? "" : 
      String.format("value=\"%s\"", rdn);
    String attrValues = Strings.isNullOrEmpty(attrs) ? "" : 
      String.format("value=\"%s\"", attrs);

    String searchForm = String.format(searchFormTemplate, 
      rdnValue, filter, attrValues);

    String recordBodyTemplate = IO.loadUtf8ResourceFromClasspath(
      "templates/record-body.html");
    String recordBody = new StringBuilder(searchForm).append(
      String.format(recordBodyTemplate, filter, recordTableBuilder.toString()))
        .toString();

    String footer = "";
    if (hostname != null && authentication != null) {
      String basedocFooterTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/basedoc-footer.html");
      footer = String.format(basedocFooterTemplate, hostname, 
        authentication);
    }

    String basedocTemplate = IO.loadUtf8ResourceFromClasspath(
      "templates/basedoc.html");
    return String.format(basedocTemplate, recordHead, recordBody, footer);
  }

}