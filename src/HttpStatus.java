enum HttpStatus {

  OK(200, "OK"),

  CREATED(201, "Created"),
  
  NO_CONTENT(204, "No Content"),
  
  FOUND (302, "Found"),
  
  TEMPORARY_REDIRECT(307, "Temporary Redirect"), 

  BAD_REQUEST(400, "Bad Request"),

  NOT_FOUND(404, "Not Found"),

  METHOD_NOT_ALLOWED(405, "Method Not Allowed"),

  INTERNAL_SERVER_ERROR(500, "Internal Server Error");
  
  private final int statusCode;

  private final String message;

  private HttpStatus(int statusCode, String message) {
    this.statusCode = statusCode;
    this.message = message;
  }

  int getStatusCode() {
    return statusCode;
  }

  String getMessage() {
    return message;
  }
}