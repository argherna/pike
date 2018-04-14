import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
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
import java.util.prefs.InvalidPreferencesFormatException;
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

  private final HttpServer httpServer;

  private final Set<HttpContext> httpContexts = new HashSet<>();

  public static void main(String... args) {
    int port = DEFAULT_HTTP_SERVER_PORT;
    int argIdx = 0;
    String connName = null;
    String filename = null;
    while (argIdx < args.length) {
      String arg = args[argIdx];
      switch (arg) {
        case "-D":
        case "--delete-all-connections":
          try {
            deleteAllConnections();
            System.exit(0);
          } catch (BackingStoreException e) {
            System.err.println(e.getMessage());
            System.exit(1);
          }
        case "-d":
        case "--delete-connection":
          connName = args[++argIdx];
          try {
            deleteConnection(connName);
            System.exit(0);
          } catch (BackingStoreException e) {
            System.err.println(e.getMessage());
            System.exit(1);
          }
        case "-h":
        case "--help":
          showUsageAndExit(2);
          break;
        case "-i":
        case "--import-connections":
          filename = args[++argIdx];
          try {
            importConnections(filename);
            System.exit(0);
          } catch (IOException | InvalidPreferencesFormatException e) {
            System.err.println(e.getMessage());
            System.exit(1);
          }
        case "-l":
        case "--list-connections":
          listConnections();
          System.exit(0);
        case "-X":
        case "--export-all-connections":
          try {
            exportAll(System.out);
            System.exit(0);
          } catch (IOException | BackingStoreException e) {
            System.err.println(e.getMessage());
            System.exit(1);
          }
          break;
        case "-x":
        case "--export-connection":
          connName = args[++argIdx];
          try {
            export(connName, System.out);
            System.exit(0);
          } catch (IOException | BackingStoreException e) {
            System.err.println(e.getMessage());
            System.exit(1);
          }
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
      
      Filter faviconFilter = new FaviconFilter();
      Filter internalServerErrorFilter = new InternalServerErrorFilter();
      Filter jsonInFilter = new JsonInFilter();
      Filter logRequestFilter = new LogRequestFilter();

      pike.addHandler("/", searchHandler, 
        List.of(logRequestFilter, faviconFilter, internalServerErrorFilter));
      pike.addHandler("/connection", new ConnectionHandler(),
        List.of(logRequestFilter, faviconFilter, internalServerErrorFilter));
      pike.addHandler("/connections", new ConnectionsHandler(),
        List.of(logRequestFilter, faviconFilter, internalServerErrorFilter));
      pike.addHandler("/css", staticResourceHandler,
        List.of(logRequestFilter, faviconFilter, internalServerErrorFilter));
      pike.addHandler("/error", new ErrorHandler(),
        List.of(logRequestFilter, faviconFilter, internalServerErrorFilter));
      pike.addHandler("/js", staticResourceHandler,
        List.of(logRequestFilter, faviconFilter, internalServerErrorFilter));
      pike.addHandler("/record", new RecordViewHandler(),
        List.of(logRequestFilter, faviconFilter, internalServerErrorFilter));
      pike.addHandler("/search", searchHandler,
        List.of(logRequestFilter, faviconFilter, internalServerErrorFilter));
      pike.addHandler("/searches", new SearchesHandler(),
        List.of(logRequestFilter, faviconFilter, jsonInFilter, 
          internalServerErrorFilter));
      pike.addHandler("/settings", new SettingsHandler(), 
        List.of(logRequestFilter, faviconFilter, internalServerErrorFilter));
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
    Preferences pikeRoot = Preferences.userRoot()
      .node(Settings.PREFERENCES_ROOT_NODE_NAME);
    pikeRoot.put(Settings.ACTIVE_CONN_NAME_SETTING, connectionName);
    pikeRoot.flush();
    pikeRoot.sync();
    LOGGER.fine(() -> 
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
    deleteConnection(connectionName);
    LOGGER.info(
      String.format("Deleted connection settings for %s", connectionName));
    String activeConnectionName = Settings.getActiveConnectionName();
    if (activeConnectionName.equals(connectionName)) {
      Preferences pikeRoot = Preferences.userRoot()
        .node(Settings.PREFERENCES_ROOT_NODE_NAME);
      pikeRoot.remove(Settings.ACTIVE_CONN_NAME_SETTING);
      pikeRoot.flush();
    }
  }

  private static void deleteAllConnections() throws BackingStoreException {
    Preferences connections = Preferences.userRoot()
      .node(Settings.CONNECTION_PREFS_ROOT_NODE_NAME);
    connections.removeNode();
    connections.flush();
  }

  private static void deleteConnection(String name) 
    throws BackingStoreException {
    Preferences connection = Settings.getConnectionSettings(name);
    connection.removeNode();
    connection.flush();
  }

  private static void export(String name, PrintStream out) 
    throws BackingStoreException, IOException {
    byte[] prefs = Settings.exportConnectionSettings(name);
    out.print(new String(prefs));
  }

  private static void exportAll(PrintStream out) 
    throws BackingStoreException, IOException {
    byte[] prefs = Settings.exportAllConnectionSettings();
    out.print(new String(prefs));
  }

  private static void importConnections(String filename) 
    throws IOException, InvalidPreferencesFormatException {
    InputStream is = new FileInputStream(filename);
    Settings.importSettings(is);
  }

  private static void listConnections() {
    Preferences connections = Preferences.userRoot()
      .node(Settings.CONNECTION_PREFS_ROOT_NODE_NAME);
    try {
      String[] children = connections.childrenNames();
      for (String child : children) {
        System.out.println(child);
      }
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
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
    System.err.println("  -D, --delete-all-connections");
    System.err.println("                   Deletes all connections");
    System.err.println("  -d <conn-name>, --delete-connection <conn-name>");
    System.err.println("                   Delete the connection settings named <conn-name>");
    System.err.println("  -h, --help       Show this help and exit");
    System.err.println("  -i <file-name>, --import-connections <file-name>");
    System.err.println("                   Import connection settings from <file-name>");
    System.err.println("  -l, --list-connections");
    System.err.println("                   List connection names and exit");
    System.err.println("  -X, --export-all-connections");
    System.err.println("                   Export all connection settings and exit");
    System.err.println("  -x <conn-name>, --export-connections <conn-name>");
    System.err.println("                   Export connection named <conn-name> and exit");
  }  

  Pike(int port) throws IOException {
    httpServer = HttpServer.create(new InetSocketAddress(port), 0);
  }

  void addHandler(String path, HttpHandler handler, List<Filter> filters) {
    LOGGER.config(() -> String.format("Registering %s with %s", path,
        handler.getClass().getSimpleName()));
    HttpContext context = httpServer.createContext(path);
    List<Filter> contextFilters = context.getFilters();
    contextFilters.addAll(filters);
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