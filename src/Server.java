import java.io.Console;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class Server {

  private static final int DEFAULT_HTTP_SERVER_PORT = 8080;

  private static final Logger LOGGER = Logger.getLogger(
      Server.class.getName());
      
  private final Filter logRequestFilter = new LogRequestFilter();

  private final Filter readOnlyMethodFilter = new ReadOnlyMethodFilter();
  
  private final HttpServer httpServer;

  private final LdapSession ldapSession;

  private final Set<HttpContext> contexts = new HashSet<>();

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
              LOGGER.config(() -> {
                return String.format(
                  "%s is not a valid port number, defaulting to %d%n",
                  args[0], DEFAULT_HTTP_SERVER_PORT);
              });
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
      LdapSession ldapSession = new LdapSession(ldapUrl, 
        searchBase, bindDn, password, useStartTls);
      StaticResourceHandler h1 = new StaticResourceHandler();
      final Server server = new Server(port, ldapSession);
      server.addHandler("/record", new RecordViewHandler());
      server.addHandler("/css", h1);
      server.addHandler("/js", h1);

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
    System.err.printf("Usage: %s [OPTIONS] [port]%n", Server.class.getName());
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

  static boolean isNullOrEmpty(String value) {
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
  
  Server(int port, LdapSession ldapSession) throws IOException {
    httpServer = HttpServer.create(new InetSocketAddress(port), 0);    
    this.ldapSession = ldapSession;
  }

  void addHandler(String path, HttpHandler handler) {
    LOGGER.config(() -> {
      return String.format("Registering %s with %s", path, 
        handler.getClass().getSimpleName());
    });
    HttpContext context = httpServer.createContext(path);
    List<Filter> filters = context.getFilters();
    filters.add(logRequestFilter);
    filters.add(readOnlyMethodFilter);
    Map<String, Object> attributes = context.getAttributes();
    attributes.put("ldapSession", ldapSession);
    context.setHandler(handler);
    contexts.add(context);
  }

  void serve() {
    LOGGER.info("Starting HTTP server...");
    for (HttpContext context : contexts) {
      LOGGER.config(() -> {
        return String.format("Server ready at http://localhost:%1$d%2$s", 
          httpServer.getAddress().getPort(), context.getPath());
      });
    }
    httpServer.start();
  }

  void shutdown() {
    for (HttpContext context : contexts) {
      LOGGER.info(String.format("Removing %s:%s", 
        context.getHandler().getClass().getSimpleName(), context.getPath()));
      httpServer.removeContext(context);
    }
    LOGGER.warning("Stopping HTTP server...");
    httpServer.stop(0);
  }
}