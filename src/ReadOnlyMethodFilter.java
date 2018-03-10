import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

class ReadOnlyMethodFilter extends Filter {

  @Override
  public String description() {
    return "Allow GET and HEAD requests only";
  }

  @Override
  public void doFilter(HttpExchange exchange, Filter.Chain chain) 
    throws IOException {
    String method = exchange.getRequestMethod();
    if (method.equals("GET") || method.equals("HEAD")) {
      // Don't write output if the request is HEAD.
      if (method.equals("HEAD")) {
        exchange.setStreams(exchange.getRequestBody(), new OutputStream() {
          @Override
          public void write(int b) throws IOException {}
        });
      }
      chain.doFilter(exchange);
    } else {
      HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;
      byte[] content = Pages.errorHtml(status, method)
        .getBytes();
      IO.sendResponseHeaders(exchange, 
        ContentTypes.TYPES.get("html"), status.getStatusCode(),
        content.length);

      if (content.length > 0) {
        OutputStream out = exchange.getResponseBody();
        out.write(content);
        out.flush();
        out.close();
      }
      exchange.close();
    }
  }
}