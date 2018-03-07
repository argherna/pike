import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;

final class RequestLogger {

  private static final Logger LOGGER = Logger.getLogger(
    RequestLogger.class.getName());
    
    static void log(final int status, final HttpExchange exchange) {
      LOGGER.info(new MessageSupplier(status, exchange));
    }
    
  private static final class MessageSupplier implements Supplier<String> {
    private static final String HTTP_DATE_LOG_FORMAT = "[dd/MMM/yyyy HH:mm:ss]";
      
    private final int status;
      
    private final HttpExchange exchange;
      
    private MessageSupplier(int status, HttpExchange exchange) {
      this.status = status;
      this.exchange = exchange;
    }

    @Override
    public String get() {
      StringBuilder message = new StringBuilder();
      message.append(exchange.getRemoteAddress().getAddress().toString())
        .append(" - - ")
        .append(new SimpleDateFormat(HTTP_DATE_LOG_FORMAT).format(new Date()))
        .append(" \"").append(exchange.getRequestMethod()).append(" ")
        .append(exchange.getRequestURI().getRawPath());
      if (exchange.getRequestURI().getRawQuery() != null && 
           !exchange.getRequestURI().getRawQuery().isEmpty()) {
        message.append("?").append(exchange.getRequestURI().getRawQuery());
      }
      message.append("\" ").append(status).append(" -");
      return message.toString();
    }
  }
}