import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

class InternalServerErrorFilter extends Filter {

  private static final Logger LOGGER = Logger.getLogger(
    InternalServerErrorFilter.class.getName());

  @Override
  public String description() {
    return "Handle exceptions not caught by the handlers";
  }

  @Override
  public void doFilter(HttpExchange exchange, Filter.Chain chain) 
    throws IOException {
    try {
      chain.doFilter(exchange);
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, 
        "Unhandled exception! Returning Internal Server Error", e);
      HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
      byte[] content = Pages.errorHtml(status, 
        "An internal error occurred! Check the server logs!").getBytes();
      IO.sendResponse(exchange, status, content, ContentTypes.TYPES.get("html"));
    }
  }
}