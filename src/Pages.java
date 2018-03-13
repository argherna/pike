import java.io.IOException;
import java.util.Collection;
import java.util.Map;

final class Pages {

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