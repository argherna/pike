import java.io.IOException;
import java.util.Collection;

final class Pages {

  static String errorHtml(HttpStatus status, String text) {
    try {
      String errorHeaderTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/error-head.html");
      String errorHeader = String.format(errorHeaderTemplate, 
        status.getStatusCode(), status.getMessage());

      String errorBodyTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/error-body.html");
      String errorBody = String.format(errorBodyTemplate, status.getMessage(),
        text);
      
      String htmlTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/basedoc.html");
      return String.format(htmlTemplate, errorHeader, errorBody);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static String recordView(String filter, Collection<StringTuple> data) {
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
  
      String recordBodyTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/record-body.html");
      String recordBody = String.format(recordBodyTemplate, filter, 
        recordTable);
  
      String basedocTemplate = IO.loadUtf8ResourceFromClasspath(
        "templates/basedoc.html");
      return String.format(basedocTemplate, recordHead, recordBody);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}