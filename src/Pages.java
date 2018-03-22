import java.io.IOException;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

final class Pages {

  private Pages() {
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

  static String errorHtml(HttpStatus status, String text) {
    return errorHtml(status, text, null, null);
  }

  static String errorHtml(HttpStatus status, String text, String hostname,
    String authentication) {
    try {
      String errorHeaderTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/error-head.html");
      String errorHeader = String.format(errorHeaderTemplate, 
        status.getStatusCode(), status.getMessage());

      String errorBodyTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/error-body.html");
      String errorBody = String.format(errorBodyTemplate, status.getMessage(),
        text);

      String footer = "";
      if (hostname != null && authentication != null) {
        String basedocFooterTemplate = IO.loadUtf8ResourceFromClasspath(
          "templates/basedoc-footer.html");
        footer = String.format(basedocFooterTemplate, hostname, 
          authentication);
      }
        
      String htmlTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/basedoc.html");
      return String.format(htmlTemplate, errorHeader, errorBody, footer);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static String renderSearch() throws IOException {
    return renderSearch(null, null);
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

  static String renderConnection(String name, String ldapUrl, String baseDn,
    String bindDn, boolean useStartTls, String mode) throws IOException {

    // TODO: Improve this so all markup comes from templates.
    try (Formatter f = new Formatter(startRender())) {
      boolean inEditMode = !Strings.isNullOrEmpty(mode) && mode.equals("edit");

      f.out().append(IO.loadUtf8ResourceFromClasspath(
        "templates/connection-open.html"));

      String pageTitle = "";
      if (Strings.isNullOrEmpty(name)) {
        pageTitle = "New Connection";
      } else if (inEditMode) {
        pageTitle = "Edit " + name;
      } else {
        pageTitle = name;
      }
      f.out().append("<title>").append(pageTitle).append("</title>");

      headToBody(f.out());

      if (!inEditMode && !Strings.isNullOrEmpty(name)) {
        f.format(IO.loadUtf8ResourceFromClasspath(
          "templates/connection-viewmode-buttons.html"), name);
      }

      f.out().append("<h1>").append(pageTitle).append("</h1>");
      f.out().append(IO.loadUtf8ResourceFromClasspath(
         "templates/connection-form-open.html"));
      
      // Opens the fieldset for the text fields
      f.out().append("<fieldset class=\"form-group row\">")
        .append("<legend class=\"col-form-legend\">Connection Details</legend>");

      renderTextboxFormGroup("templates/connection-fg-name.html", f, name, 
        mode);
      renderTextboxFormGroup("templates/connection-fg-server-url.html", f, 
        ldapUrl, mode);
      renderTextboxFormGroup("templates/connection-fg-basedn.html", f, 
        baseDn, mode);
      renderTextboxFormGroup("templates/connection-fg-binddn.html", f, 
        bindDn, mode);
      // DON'T RENDER THE PASSWORD!!!!
      renderTextboxFormGroup("templates/connection-fg-password.html", f, 
        "", mode);

      // Close the fieldset.
      f.out().append("</fieldset>");

      // Open a new fieldset for connection options.
      f.out().append("<fieldset class=\"form-group row\">")
        .append("<legend class=\"col-form-legend\">Connection Options</legend>");
      
      renderCheckboxFormGroup("templates/connection-fg-fc-usestarttls.html", 
        f, useStartTls, mode);

      // Close the fieldset.
      f.out().append("</fieldset>");
      
      // Add a link to return to the list of connections.
      if (!inEditMode) {
        f.out().append("<div class=\"float-right\">")
          .append("<a href=\"/connections\" class=\"btn btn-light\">Return to Connections List</a>")
          .append("</div>");
      }

      // Render the form buttons.
      renderConnectionButtonGroup(f, name, inEditMode);

      // If editing, add a Cancel button.
      if (inEditMode && !Strings.isNullOrEmpty(name)) {
        f.format("<a href=\"%s\" class=\"btn btn-light\">Cancel</a>", name);
      }

      f.out().append("</span></div></form></article></div></section>");
    
      endRender(f.out());
      return f.toString();
    }
  }

  static String renderConnections(List<StringTuple> connectionNames) 
    throws IOException {
    try (Formatter f = new Formatter(startRender())) {
      f.out().append("<title>Connections</title>");
      headToBody(f.out());

      f.out().append("<h1>Connections</h1>");
      f.out().append(IO.loadUtf8ResourceFromClasspath(
        "templates/connections-open-list.html"));
      for (StringTuple connectionName : connectionNames) {
        f.format("<li><a href=\"/connection/%1$s\">%1$s</a> %2$s</li>", 
          connectionName.s1, connectionName.s2);
      }
      f.out().append(IO.loadUtf8ResourceFromClasspath(
        "templates/connections-close-list.html"));
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

  private static Formatter renderTextboxFormGroup(String templateName, 
    Formatter f, String parameter, String mode) throws IOException {
    String value = "";
    if (!Strings.isNullOrEmpty(parameter)) {
      value = value + "value=\"" + parameter + "\" ";
    }
    if (Strings.isNullOrEmpty(mode) || !mode.equals("edit")) {
      value = value + "disabled";
    }
    return f.format(IO.loadUtf8ResourceFromClasspath(templateName), value);
  }

  private static Formatter renderCheckboxFormGroup(String templateName,
    Formatter f, boolean parameter, String mode) throws IOException {
    String value = parameter ? "checked " : "";

    if (Strings.isNullOrEmpty(mode) || !mode.equals("edit")) {
      value = value + "disabled";
    }
    return f.format(IO.loadUtf8ResourceFromClasspath(templateName), value);
  }

  private static Formatter renderConnectionButtonGroup(Formatter f, 
    String name, boolean inEditMode) throws IOException {
    String disabled = inEditMode ? "" : "disabled";
    return f.format(IO.loadUtf8ResourceFromClasspath(
      "templates/connection-fg-bg.html"), disabled);
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

  static String connections() throws IOException {
    String basedocTemplate = IO.loadUtf8ResourceFromClasspath(
      "templates/basedoc.html");
    return String.format(basedocTemplate, "<title>Connections</title>", "<h1>Connections</h1><p>Connections list goes here.", "");
  }

  public static void main(String... args) {
    try {
      for (int i = 0; i < 100; i++) {
      long startWithFormatter = System.currentTimeMillis();
      renderError(HttpStatus.INTERNAL_SERVER_ERROR, "A bad error occurred",
        "localhost","uid=foo");
      long endWithFormatter = System.currentTimeMillis();
      long startWithStringFormat = System.currentTimeMillis();
      errorHtml(HttpStatus.INTERNAL_SERVER_ERROR, "A bad error occurred", 
        "localhost", "uid=foo");
      long endWithStringFormat = System.currentTimeMillis();
      System.out.printf("    Time with formatter: %d MS%n", 
        (endWithFormatter - startWithFormatter));
      System.out.printf("Time with String.format: %d MS%n",
        (endWithStringFormat - startWithStringFormat));
      }
    } catch (IOException e) {
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
}