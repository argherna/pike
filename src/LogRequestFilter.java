import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Filter.Chain;
import com.sun.net.httpserver.HttpExchange;

class LogRequestFilter extends Filter {
  
  private static final Logger LOGGER = Logger.getLogger(
    LogRequestFilter.class.getName());

  private static final String HTTP_DATE_LOG_FORMAT = 
    "[dd/MMM/yyyy HH:mm:ss]";

  @Override
  public String description() {
    return "Log server requests in Apache common log format";
  }

  @Override
  public void doFilter(final HttpExchange exchange, Filter.Chain chain) 
    throws IOException {
    chain.doFilter(exchange);
    LOGGER.info(() -> {
      String userId = exchange.getPrincipal() != null ? 
        exchange.getPrincipal().getUsername() : "-";
      String timestamp = new SimpleDateFormat(HTTP_DATE_LOG_FORMAT)
        .format(new Date());
      String path = exchange.getRequestURI().getRawQuery() != null ? 
        exchange.getRequestURI().getRawPath() + "?" + 
          exchange.getRequestURI().getRawQuery() :
        exchange.getRequestURI().getRawPath();
      String contentLengthHeader = exchange.getResponseHeaders()
        .getFirst("Content-Length");
      String contentLength = contentLengthHeader == null ? "-" :
        contentLengthHeader;
      
      return new StringBuilder(
        exchange.getRemoteAddress().getAddress().toString()).append(" - ")
        .append(userId).append(" ").append(timestamp).append(" \"")
        .append(exchange.getRequestMethod()).append(" ").append(path)
        .append(" ").append(exchange.getProtocol()).append("\" ")
        .append(exchange.getResponseCode()).append(" ").append(contentLength)
        .toString();
    });
  }
}