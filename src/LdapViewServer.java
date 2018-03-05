
import java.io.Console;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.SSLSession;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Web viewer for LDAP entries.
 */
class LdapViewServer {

  private static final int DEFAULT_HTTP_SERVER_PORT = 8080;

  private static final Logger LOGGER = Logger.getLogger(LdapViewServer.class.getName());

  private final HttpServer httpServer;

  private final LdapViewHandler ldapViewHandler;

  /**
   * Control execution of the server.
   */
  public static void main(String... args) {

    if (args.length == 0) {
      showUsageAndExit(2);
    }

    int port = DEFAULT_HTTP_SERVER_PORT;

    int argIdx = 0;
    String bindDn = null;
    String ldapUrl = null;
    String password = null;
    boolean promptForPassword = false;
    String searchBase = null;
    boolean useStartTls = false;

    while (argIdx < args.length) {
      String arg = args[argIdx];
      switch (arg) {
        case "-b":
          searchBase = args[++argIdx];
          break;
        case "-D":
          bindDn = args[++argIdx];
          break;
        case "-h":
        case "--help":
          showUsageAndExit(2);
          break;
        case "-H":
          ldapUrl = args[++argIdx];
          break;
        case "-w":
          password = args[++argIdx];
          break;
        case "-W":
          promptForPassword = true;
          break;
        case "-Z":
          useStartTls = true;
          break;
        default:
          if (arg.startsWith("-")) {
            System.err.printf("Unknown option %s%n", arg);
            showUsageAndExit(1);
          } else {
            try {
              port = Integer.valueOf(arg);
            } catch (NumberFormatException e) {
              LOGGER.config(String.format("%s is not a valid port number, defaulting to %d%n", 
                args[0], DEFAULT_HTTP_SERVER_PORT));
              port = DEFAULT_HTTP_SERVER_PORT;
            }
          }
          break;
      }
      argIdx++;
    }

    if (isNullOrEmpty(bindDn)) {
      System.err.println("-D not set!");
      showUsageAndExit(1);
    }

    if (isNullOrEmpty(ldapUrl)) {
      System.err.println("-H not set!");
      showUsageAndExit(1);
    }

    if (isNullOrEmpty(searchBase)) {
      System.err.println("-b not set!");
      showUsageAndExit(1);
    }

    if (promptForPassword && isNullOrEmpty(password)) {
      password = new String(getPassword("bind"));
    }

    if (isNullOrEmpty(password)) {
      System.err.println("Password not set!");
      showUsageAndExit(1);
    }

    try {
      final LdapViewServer server = new LdapViewServer(port, ldapUrl, 
        searchBase, bindDn, password, useStartTls);
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          server.shutdown();
        }
      });
      server.serve();
    } catch (Exception e) {
      System.err.printf("%s%n", e.getMessage());
      System.exit(1);
    }
  }

  private static void showUsageAndExit(int status) {
    showUsage();
    System.exit(status);
  }

  private static void showUsage() {
    System.err.printf("Usage: %s [OPTIONS] [port]%n", LdapViewServer.class);
    System.err.println();
    System.err.println("Serves pages of LDAP entries.");
    System.err.println();
    System.err.println("Arguments:");
    System.err.println();
    System.err.println("  port             port the server will listen on (default is 8080)");
    System.err.println();
    System.err.println("Options:");
    System.err.println();
    System.err.println("  -b <searchbase>  base for searches");
    System.err.println("  -D <bindDn>      bind DN");
    System.err.println("  -h, --help       Show this help and exit");
    System.err.println("  -H <url>         LDAP URL");
    System.err.println("  -w <password>    Bind password");
    System.err.println("  -W               Prompt for password");
    System.err.println("  -Z               Use StartTLS");
  }

  private static boolean isNullOrEmpty(String value) {
    return (value == null || (value != null && value.isEmpty()));
  }

  private static char[] getPassword(String passwordType) {
    // Set a default password just in case there's no Console available.
    char[] password = "abcd1234".toCharArray();
    Console c = System.console();
    if (c == null) {
      System.err.println("System console not available!");
    } else {
      password = c.readPassword("Enter %s password:  ", passwordType);
    }
    return password;
  }

  LdapViewServer(int port, String ldapUrl, String searchBase, String bindDn, 
    String password, boolean useStartTls) throws IOException {
    httpServer = HttpServer.create(new InetSocketAddress(port), 0);
    ldapViewHandler = new LdapViewHandler(ldapUrl, searchBase, bindDn, 
      password, useStartTls);
  }

  void serve() {
    LOGGER.config("Starting HTTP server...");
    httpServer.createContext("/dir", ldapViewHandler);
    LOGGER.config(String.format("Server ready at http://localhost:%1$d/dir", 
      httpServer.getAddress().getPort()));
    httpServer.start();
  }

  void shutdown() {
    ldapViewHandler.shutdown();
    LOGGER.warning("Stopping HTTP server...");
    httpServer.stop(0);
  }

  /**
   */
  private static class LdapViewHandler implements HttpHandler {
    
    private static final Map<String, String> TYPES;

    static {
      Map<String, String> types = new HashMap<>();
      types.put("css", "text/css");
      types.put("gif", "image/gif");
      types.put("html", "text/html");
      types.put("jpg", "image/jpeg");
      types.put("js", "application/javascript");
      types.put("png", "image/png");
      types.put("svg", "image/svg+xml");
      types.put("woff", "application/x-font-woff");
      types.put("eot", "application/vnd.ms-fontobject");
      types.put("ttf", "application/octet-stream");
      types.put("otf", "application/octet-stream");
      TYPES = Collections.unmodifiableMap(types);
    }

    private static final String HTML_DOC_TEMPLATE = 
      "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n%1$s\n</head>\n<body>\n" +
        "%2$s\n</body>\n</html>";

    private static final String HTML_RESULTS_TEMPLATE = 
      "<table>\n<thead>\n<tr>\n<th scope=\"col\">Attribute</th>\n<th>Value" +
        "</th>\n</tr>\n</thead>\n<tbody>\n%1$s</tbody>\n</table>";

    private static final String HTML_TABLE_ROW_1_TEMPLATE = 
      "<tr style=\"rowColor\">\n<td>%1$s</td>\n<td>%2$s</td>\n</tr>\n";

    private static final String HTML_TABLE_ROW_2_TEMPLATE = 
      "<tr style=\"altColor\">\n<td>%1$s</td>\n<td>%2$s</td>\n</tr>\n";

    private static final String ERROR_HTML_TEMPLATE = "<html><head><title>" +
      "%1$d %2$s</title></head><body><h1>%2$s</h1><p>%3$s</p></body></html>";

    private static final String HTTP_DATE_LOG_FORMAT = "[dd/MMM/yyyy HH:mm:ss]";

    private final LdapContext ldapContext;

    private final StartTlsResponse tls;

    private final SSLSession sslSession;

    private final String searchBase;

    private LdapViewHandler(String ldapUrl, String searchBase, String bindDn, 
      String password, boolean useStartTls) {
      this.searchBase = searchBase;
      Hashtable<String, String> env = new Hashtable<>();
      env.put(Context.INITIAL_CONTEXT_FACTORY, 
        "com.sun.jndi.ldap.LdapCtxFactory");
      env.put(Context.PROVIDER_URL, ldapUrl);

      try {
        ldapContext = new InitialLdapContext(env, null);
        if (useStartTls) {
          LOGGER.config("Starting TLS session...");
          tls = (StartTlsResponse) ldapContext.extendedOperation(
              new StartTlsRequest());
          sslSession = tls.negotiate();
        } else {
          tls = null;
          sslSession = null;
        }
        ldapContext.addToEnvironment(Context.SECURITY_AUTHENTICATION, 
          "simple");
        ldapContext.addToEnvironment(Context.SECURITY_PRINCIPAL, 
          bindDn);
        ldapContext.addToEnvironment(Context.SECURITY_CREDENTIALS,
          password);
        ldapContext.addToEnvironment(Context.REFERRAL, "ignore");
        LOGGER.config(() -> {
          return String.format("Connected to %s; search base = %s", ldapUrl, 
            searchBase);
        });
      } catch (IOException | NamingException e) {
        LOGGER.severe(String.format("Error initializing LDAP connection: %s%n", e.getMessage()));
        throw new RuntimeException(e);
      }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      
      byte[] content = new byte[0];
      int status = 200;
      if (exchange.getRequestMethod().equals("GET")) {
        content = handleGet(exchange);
      } else if (exchange.getRequestMethod().equals("HEAD")) {
        content = new byte[0];
      } else {
        status = 405;
        content = getErrorHtml(status, "Method not allowed", 
          exchange.getRequestMethod()).getBytes();
      }
      String contentType = TYPES.get("html");
      try {
        Headers h = exchange.getResponseHeaders();
        h.add("Content-Type", contentType);
        h.add("Server", String.format("%s/Java %s", LdapViewServer.class.getName(), 
        System.getProperty("java.version")));
        exchange.sendResponseHeaders(status, content.length);          
      } catch (IOException e) {
        LOGGER.warning(String.format("Problem sending response headers", e));
      }

      if (content.length > 0) {
        OutputStream out = exchange.getResponseBody();
        out.write(content);
        out.flush();
      }

      // Log the request
      LOGGER.info(
        String.format("%1$s - - %2$s \"%3$s %4$s\" %5$d -", 
          exchange.getRemoteAddress().getAddress().toString(),
          new SimpleDateFormat(HTTP_DATE_LOG_FORMAT).format(new Date()), 
            exchange.getRequestMethod(), exchange.getRequestURI().getPath(), 
          status));
      exchange.close();
    }

    private byte[] handleGet(HttpExchange exchange) {

      URI requestUri = exchange.getRequestURI();
      if (requestUri.getPath().contains("/record/base")) {
        String base = getBaseFromPath(requestUri.getPath());
        if (!isNullOrEmpty(base)) {
          LOGGER.fine(String.format("search base = %s", base));
          try {
            String filter = getFilterFromQuery(requestUri.getRawQuery());
            LOGGER.fine(String.format("filter = %s", filter));
              
            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            NamingEnumeration<SearchResult> result = ldapContext.search(
              base, filter, searchControls);
            List<Tuple> searchResults = loadResults(result);
            Collections.sort(searchResults);

            StringBuilder resultRows = new StringBuilder();
            int row = 0;
            for (Tuple tuple : searchResults) {
              String template = row % 2 == 0 ? HTML_TABLE_ROW_1_TEMPLATE : 
                HTML_TABLE_ROW_2_TEMPLATE;
              resultRows.append(String.format(template, tuple.s1, tuple.s2))
                .append("\n");
              row++;
            }
            String resultTable = String.format(HTML_RESULTS_TEMPLATE, 
              resultRows.toString());
            String title = String.format("<title>Results: %s</title>", filter);
            String content = String.format(HTML_DOC_TEMPLATE, title, resultTable);
            return content.getBytes();
          } catch (PartialResultException e) {
            LOGGER.log(Level.FINE, String.format("Ignoring %s", 
                e.getClass().getName()), e);
          } catch (NamingException e) {
            LOGGER.severe(String.format("Lookup failure: %s%n", e));
          }
        }
      } else {
        // 404
      }

      String content = String.format(HTML_DOC_TEMPLATE, "<title>Viewer</title>", 
        "<h1>Success</h1>\n<p>It Works!</p>");
      return content.getBytes();
    }

    private List<Tuple> loadResults(NamingEnumeration<SearchResult> result) 
      throws NamingException {
      List<Tuple> attributes = new ArrayList<>();
      while (result.hasMore()) {
        try {
          SearchResult sr = result.next();
          Attributes attrs = sr.getAttributes();
          NamingEnumeration<String> attrNames = attrs.getIDs();
          while (attrNames.hasMore()) {
            String name = attrNames.next();
            attributes.addAll(attrsToTuples(attrs, name));
          }
        } catch (PartialResultException e) {
          LOGGER.log(Level.FINE, String.format("Ignoring %s", 
              e.getClass().getName()), e);
        }
      }
      return attributes;
    }

    private List<Tuple> attrsToTuples(Attributes attrs, String name) 
      throws NamingException {
      List<Tuple> attributes = new ArrayList<>();
	  Attribute attr = attrs.get(name);
      NamingEnumeration<?> attrvals = attr.getAll();
      while (attrvals.hasMore()) {
        Object v = attrvals.next();
        Tuple t = null;
        if (v instanceof String) {
          t = new Tuple(attr.getID(), v.toString());
        } else {
          t = new Tuple(attr.getID(), v.getClass().getSimpleName());
        }
        attributes.add(t);
      }
      return attributes;
	}

    private String getBaseFromPath(String path) {
      String[] baseComponents = path.split(";");
      StringJoiner joiner = new StringJoiner(",");
      for (String baseComponent : baseComponents) {
        if (!baseComponent.startsWith("/")) {
          joiner.add(baseComponent);
        }
      }
      return String.format("%s,%s", joiner.toString(), searchBase);
    }

    private String getFilterFromQuery(String query) {
      String filter = "(objectClass=*)";
      if (!isNullOrEmpty(query)) {
        String[] params = query.split("=");
        try {
          filter = URLDecoder.decode(params[1], "UTF-8");
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      return filter;
    }

    private String getErrorHtml(int status, String error, String text) {
      return String.format(ERROR_HTML_TEMPLATE, status, error, text);
    }
  
    void shutdown() {
      LOGGER.warning("Disconnecting from LDAP Server...");
      if (sslSession != null) {
        sslSession.invalidate();
      }
      if (tls != null) {
        try {
          tls.close();
        } catch (IOException e) {
          LOGGER.warning(String.format("Failed to close TLS response: %s%n",
            e.getMessage()));
        }
      }
      try {
        ldapContext.close();
      } catch (NamingException e) {
        LOGGER.warning(String.format("Failed to close LDAP connection: %s%n", 
          e.getMessage()));
      }
    }
  }

  private static final class Tuple implements Comparable<Tuple> {

    public final String s1;

    public final String s2;

    private Tuple(String s1, String s2) {
      this.s1 = s1;
      this.s2 = s2;
    }

    @Override
    public int compareTo(Tuple t) {
      int s1Compare = this.s1.compareTo(t.s1);
      if (s1Compare == 0) {
        return this.s2.compareTo(t.s2);
      } else {
        return s1Compare;
      }
    }
  }
}