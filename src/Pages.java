import java.io.IOException;

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
}