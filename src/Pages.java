import java.io.IOException;
import java.util.Collection;

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

  static String recordView(String filter, Collection<StringTuple> data,
    String hostname, String authentication) {
    try {
      String recordRowTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/record-table-row.html");
      StringBuilder recordRows = new StringBuilder();
      for (StringTuple result : data) {
        recordRows.append(String.format(recordRowTemplate, result.s1, 
          result.s2)).append("\n");
      }
  
      String tableTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/record-table.html");
      String recordTable = String.format(tableTemplate, 
        recordRows.toString());
      
      String recordHeadTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/record-head.html");
      String recordHead = String.format(recordHeadTemplate, filter);
      
      String searchForm = IO.loadUtf8ResourceFromClasspath(
        "templates/search-form.html");
      
      String recordBodyTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/record-body.html");
      String recordBody = new StringBuilder(searchForm).append(
        String.format(recordBodyTemplate, filter, recordTable))
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
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}