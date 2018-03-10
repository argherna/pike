import java.io.IOException;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Filter.Chain;
import com.sun.net.httpserver.HttpExchange;

class LogRequestFilter extends Filter {

  @Override
  public String description() {
    return "Log server requests in Apache common log format";
  }

  @Override
  public void doFilter(final HttpExchange exchange, Filter.Chain chain) 
    throws IOException {
    chain.doFilter(exchange);
    RequestLogger.logRequest(exchange);
  }
}