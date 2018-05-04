import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

class NotModifiedFilter extends Filter {

  private List<String> STATIC_PATHS = List.of("/js", "/css");

  private static final String HTTP_DATE_FORMAT = 
    "EEE, dd MMM yyyy HH:mm:ss z";

  @Override
  public String description() {
    return "Generates an ETag header by calculating the checksum of the " +
      "response body. If an If-None-Match header is sent and the checksum " +
      "of the body is the same as the value of the header, a 304 status is " +
      "returned with no content.";
  }

  @Override
  public void doFilter(HttpExchange exchange, Filter.Chain chain)
    throws IOException {
    if (exchange.getRequestMethod().equals("GET") || 
      exchange.getRequestMethod().equals("HEAD")) {
      if (requestIsForStaticResource(exchange.getRequestURI().getPath())) {
        String path = exchange.getRequestURI().getPath().startsWith("/") ? 
            exchange.getRequestURI().getPath().substring(1) : 
            exchange.getRequestURI().getPath();
        long mTime = IO.lastMTime(path);
        LastModifiedHttpExchange lastModified = 
          new LastModifiedHttpExchange(exchange, mTime);
        Headers in = exchange.getRequestHeaders();
        if (in.containsKey("If-Modified-Since")) {
          String ifModifiedSinceValue = in.getFirst("If-Modified-Since");
          long inTimestamp = 0l;
          try {
            SimpleDateFormat fmt = new SimpleDateFormat(HTTP_DATE_FORMAT);
            inTimestamp = fmt.parse(ifModifiedSinceValue).getTime();
          } catch (ParseException e) {
            throw new RuntimeException(e);
          }
          if (inTimestamp != mTime) {
            chain.doFilter(lastModified);
            lastModified.doResponse();
          } else {
            lastModified.doResponse();
          }
        } else {
          chain.doFilter(lastModified);
          lastModified.doResponse();
        }
      } else {
        ETagHttpExchange etag = new ETagHttpExchange(exchange);
        chain.doFilter(etag);
        etag.doResponse();
      }
    } else {
      chain.doFilter(exchange);
    }
  }

  private boolean requestIsForStaticResource(String path) {
    return STATIC_PATHS.stream().anyMatch(p -> path.startsWith(p));
  }

  private static abstract class CaptureResponseHttpExchange extends HttpExchange {

    final HttpExchange exchange;

    ByteArrayOutputStream responseBody;

    int statusCode = -1;

    long responseLength = -1l;

    private CaptureResponseHttpExchange(HttpExchange exchange) {
      this.exchange = exchange;
      responseBody = new ByteArrayOutputStream();
    }

    @Override
    public void close() {
      // No-Op for this instance. We'll call close in #send().
    }

    @Override
    public Object getAttribute(String name) {
      return exchange.getAttribute(name);
    }

    @Override
    public HttpContext getHttpContext() {
      return exchange.getHttpContext();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
      return exchange.getLocalAddress();
    }

    @Override
    public HttpPrincipal getPrincipal() {
      return exchange.getPrincipal();
    }

    @Override
    public String getProtocol() {
      return exchange.getProtocol();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
      return exchange.getRemoteAddress();
    }

    @Override
    public InputStream getRequestBody() {
      return exchange.getRequestBody();
    }

    @Override
    public Headers getRequestHeaders() {
      return exchange.getRequestHeaders();
    }

    @Override
    public Headers getResponseHeaders() {
      return exchange.getResponseHeaders();
    }

    @Override
    public URI getRequestURI() {
      return exchange.getRequestURI();
    }

    @Override
    public String getRequestMethod() {
      return exchange.getRequestMethod();
    }

    @Override
    public OutputStream getResponseBody() {
      if (responseBody.size() > 0) {
        responseBody.reset();
      }
      return responseBody;
    }

    @Override
    public int getResponseCode() {
      return exchange.getResponseCode();
    }

    @Override
    public void sendResponseHeaders(int rCode, long responseLength) 
      throws IOException {
        this.statusCode = rCode;
        this.responseLength = responseLength;
    }

    @Override
    public void setAttribute(String name, Object value) {
      exchange.setAttribute(name, value);
    }

    @Override
    public void setStreams(InputStream i, OutputStream o) {
      exchange.setStreams(i, o);
    }

    abstract void doResponse() throws IOException;

    void send(byte[] content) throws IOException {
      exchange.sendResponseHeaders(statusCode, responseLength);
      if (responseLength > 0l) {
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(content);
          out.flush();
        }
      }

      exchange.close();
    }
  }

  private static class ETagHttpExchange extends CaptureResponseHttpExchange {

    private ETagHttpExchange(HttpExchange exchange) {
      super(exchange);
    }

    @Override
    void doResponse() throws IOException {
      byte[] content = responseBody.toByteArray();
      Checksum checksum = new CRC32C();
      checksum.update(content);

      Headers in = exchange.getRequestHeaders();
      if (in.containsKey("If-None-Match")) {
        String inm = in.getFirst("If-None-Match");
        if (inm.equals(Long.toHexString(checksum.getValue()))) {
          this.statusCode = HttpStatus.NOT_MODIFIED.getStatusCode();
          this.responseLength = -1l;
        } else {
          setETag(checksum);
        }
      } else {
        setETag(checksum);
      }

      send(content);
    }

    private void setETag(Checksum checksum) {
      Headers out = exchange.getResponseHeaders();

      // HAHA! I LOVE PUNS!!!!!!
      out.put("ETag", List.of(Long.toHexString(checksum.getValue())));
    }
  }

  private static class LastModifiedHttpExchange extends CaptureResponseHttpExchange {

    private final long mTime;

    private LastModifiedHttpExchange(HttpExchange exchange, long mTime) {
      super(exchange);
      this.mTime = mTime;
    }

    @Override
    void doResponse() throws IOException {
      if (responseLength > 0l) {
        Headers out = exchange.getResponseHeaders();
        SimpleDateFormat fmt = new SimpleDateFormat(HTTP_DATE_FORMAT);
        // OMG@!! THERE IT IS AGAIN!!!! HAAAAAAHAAHAHA!
        out.put("Last-Modified", List.of(fmt.format(mTime)));
      } else {
        this.statusCode = HttpStatus.NOT_MODIFIED.getStatusCode();
      }
      send(responseBody.toByteArray());
    }
  }
}
