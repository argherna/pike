import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class Pike {

  static final String SERVER_STRING = String.format("pike/Java %s", 
    System.getProperty("java.version"));
  
  private static final int DEFAULT_HTTP_SERVER_PORT = 8085;

  private static final Logger LOGGER = Logger.getLogger(Pike.class.getName());

  private static final Map<String, LdapContext> ldapContexts = new HashMap<>();

  private final Filter faviconFilter = new FaviconFilter();

  private final Filter internalServerErrorFilter = 
    new InternalServerErrorFilter();

  private final Filter logRequestFilter = new LogRequestFilter();

  private final HttpServer httpServer;

  private final Set<HttpContext> httpContexts = new HashSet<>();

  public static void main(String... args) {
    int port = DEFAULT_HTTP_SERVER_PORT;
    int argIdx = 0;

    while (argIdx < args.length) {
      String arg = args[argIdx];
      switch (arg) {
        case "-h":
        case "--help":
          showUsageAndExit(2);
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

    try {
      final Pike pike = new Pike(port);
      HttpHandler searchHandler = new SearchHandler();
      HttpHandler staticResourceHandler = new StaticResourceHandler();
      pike.addHandler("/", searchHandler);
      pike.addHandler("/connection", new ConnectionHandler());
      pike.addHandler("/connections", new ConnectionsHandler());
      pike.addHandler("/css", staticResourceHandler);
      pike.addHandler("/error", new ErrorHandler());
      pike.addHandler("/js", staticResourceHandler);
      pike.addHandler("/record", new RecordViewHandler());
      pike.addHandler("/search", searchHandler);
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          pike.shutdown();
        }
      });
      pike.serveHttp();
    } catch (Exception e) {
      System.err.printf("%s%n", e.getMessage());
      System.exit(1);
    }
  }

  static LdapContext activate(String connectionName) 
    throws BackingStoreException, IOException, NamingException,
    NoSuchAlgorithmException, CertificateException, KeyStoreException,
    UnrecoverableKeyException {
    LdapContext active = ldapContexts.get(connectionName);
    if (active == null) {
      active = Ldap.createLdapContext(connectionName);
      ldapContexts.put(connectionName, active);
    }
    Settings.saveActiveConnectionName(connectionName);
    LOGGER.info(() -> 
      String.format("%s is the active LDAP connection", connectionName)
    );
    return active;
  }

  static LdapContext getActiveLdapContext() throws IOException, 
    NamingException, NoSuchAlgorithmException, CertificateException,
    KeyStoreException, UnrecoverableKeyException {
    LdapContext active = null;
    String activeConnectionName = Settings.getActiveConnectionName();
    if (!activeConnectionName.isEmpty()) {
      if (ldapContexts.containsKey(activeConnectionName)) {
        active = ldapContexts.get(activeConnectionName);
      } else {
        active = Ldap.createLdapContext(activeConnectionName);
        ldapContexts.put(activeConnectionName, active);
      }
    }
    return active;
  }

  static String getActiveBaseDn() {
    String activeConnectionName = Settings.getActiveConnectionName();
    String activeBaseDn = Settings.getConnectionSettings(activeConnectionName)
      .get(Settings.BASE_DN_SETTING, "");
    return activeBaseDn;
  }

  static void delete(String connectionName) throws NamingException,
    BackingStoreException {
    LdapContext toDelete = ldapContexts.remove(connectionName);
    if (toDelete != null) {
      toDelete.close();
      LOGGER.info(() -> String.format("Deleted %s", connectionName));
    }
    Preferences connection = Settings.getConnectionSettings(connectionName);
    connection.removeNode();
    connection.flush();
    LOGGER.info(
      String.format("Deleted connection settings for %s", connectionName));
    String activeConnectionName = Settings.getActiveConnectionName();
    if (activeConnectionName.equals(connectionName)) {
      Settings.deleteActiveConnectionName();
    }
  }

  private static void showUsageAndExit(int status) {
    showUsage();
    System.exit(status);
  }

  private static void showUsage() {
    System.err.printf("Usage: %s [port]%n", Pike.class.getName());
    System.err.println();
    System.err.println("Serves pages of LDAP entries.");
    System.err.println();
    System.err.println("Arguments:");
    System.err.println();
    System.err.println("  port             port the server will listen on (default is " 
      + DEFAULT_HTTP_SERVER_PORT + ")");
    System.err.println();
    System.err.println("Options:");
    System.err.println();
    System.err.println("  -h, --help       Show this help and exit");
  }  

  Pike(int port) throws IOException {
    httpServer = HttpServer.create(new InetSocketAddress(port), 0);
  }

  void addHandler(String path, HttpHandler handler) {
    LOGGER.config(() -> String.format("Registering %s with %s", path,
        handler.getClass().getSimpleName()));
    HttpContext context = httpServer.createContext(path);
    List<Filter> filters = context.getFilters();
    filters.add(logRequestFilter);
    filters.add(faviconFilter);
    filters.add(internalServerErrorFilter);
    context.setHandler(handler);
    httpContexts.add(context);
  }

  void serveHttp() {
    LOGGER.info("Starting HTTP server...");
    for (HttpContext context : httpContexts) {
      LOGGER.config(() -> 
        String.format("Server ready at http://localhost:%1$d%2$s", 
          httpServer.getAddress().getPort(), context.getPath())
      );
    }
    httpServer.start();
  }

  void shutdown() {
    for (String connectionName : ldapContexts.keySet()) {
      LdapContext ldapContext = ldapContexts.get(connectionName);
      if (ldapContext != null) {
        try {
          ldapContext.close();
          LOGGER.info(String.format("Removed LDAP connection %s.", 
            connectionName));
        } catch (NamingException e) {
          LOGGER.log(Level.WARNING, "Problem closing LdapContext on shutdown!",
            e);
        }
      }
    }

    httpContexts.stream().forEach(ctx -> {
      LOGGER.info(String.format("Removing %s:%s", 
        ctx.getHandler().getClass().getSimpleName(), ctx.getPath()));
      httpServer.removeContext(ctx);
    });
    LOGGER.warning("Stopping HTTP server...");
    httpServer.stop(0);
  }
}