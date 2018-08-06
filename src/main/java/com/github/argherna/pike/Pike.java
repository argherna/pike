package com.github.argherna.pike;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class Pike {

  private static final int DEFAULT_HTTP_SERVER_PORT = 8085;

  private static final Logger LOGGER = Logger.getLogger(Pike.class.getName());

  private static final Map<String, LdapContext> ldapContexts = new HashMap<>();

  private final HttpServer httpServer;

  public static void main(String... args) {
    var port = DEFAULT_HTTP_SERVER_PORT;
    var argIdx = 0;
    String connName = null;
    String filename = null;
    while (argIdx < args.length) {
      var arg = args[argIdx];
      switch (arg) {
      case "-D":
      case "--delete-all-connections":
        Settings.deleteAllConnections();
        System.exit(0);
      case "-d":
      case "--delete-connection":
        connName = args[++argIdx];
        Settings.deleteSingleConnection(connName);
        System.exit(0);
      case "-h":
      case "--help":
        showUsageAndExit(2);
        break;
      case "-i":
      case "--import-connections":
        filename = args[++argIdx];
        try {
          Settings.importSettings(new FileInputStream(filename));
          System.exit(0);
        } catch (IOException e) {
          System.err.println(e.getMessage());
          System.exit(1);
        }
      case "-l":
      case "--list-connections":
        Arrays.stream(Settings.getAllConnectionNames()).forEach(System.out::println);
        System.exit(0);
      case "-X":
      case "--export-all-connections":
        System.out.println(new String(Settings.exportAllConnectionSettings()));
        System.exit(0);
        break;
      case "-x":
      case "--export-connection":
        connName = args[++argIdx];
        System.out.println(new String(Settings.exportConnectionSettings(connName)));
        System.exit(0);
      default:
        if (arg.startsWith("-")) {
          System.err.printf("Unknown option %s%n", arg);
          showUsageAndExit(1);
        } else {
          try {
            port = Integer.valueOf(arg);
          } catch (NumberFormatException e) {
            LOGGER.config(() -> {
              return String.format("%s is not a valid port number, defaulting to %d%n", args[0],
                  DEFAULT_HTTP_SERVER_PORT);
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
      var searchHandler = new SearchHandler();
      var staticResourceHandler = new StaticResourceHandler();

      var faviconFilter = new FaviconFilter();
      var internalServerErrorFilter = new InternalServerErrorFilter();
      var jsonInFilter = new JsonInFilter();
      var notModifiedFilter = new NotModifiedFilter();

      pike.addHandler("/", searchHandler, List.of(notModifiedFilter, faviconFilter, internalServerErrorFilter));
      pike.addHandler("/connection", new ConnectionHandler(),
          List.of(internalServerErrorFilter, notModifiedFilter, faviconFilter));
      pike.addHandler("/connections", new ConnectionsHandler(),
          List.of(internalServerErrorFilter, notModifiedFilter, faviconFilter));
      pike.addHandler("/css", staticResourceHandler,
          List.of(internalServerErrorFilter, notModifiedFilter, faviconFilter));
      pike.addHandler("/error", new ErrorHandler(),
          List.of(internalServerErrorFilter, notModifiedFilter, faviconFilter));
      pike.addHandler("/js", staticResourceHandler,
          List.of(internalServerErrorFilter, notModifiedFilter, faviconFilter));
      pike.addHandler("/record", new RecordViewHandler(),
          List.of(internalServerErrorFilter, notModifiedFilter, faviconFilter));
      pike.addHandler("/search", searchHandler, List.of(internalServerErrorFilter, notModifiedFilter, faviconFilter));
      pike.addHandler("/searches", new SearchesHandler(),
          List.of(internalServerErrorFilter, notModifiedFilter, faviconFilter, jsonInFilter));
      pike.addHandler("/settings", new SettingsHandler(),
          List.of(internalServerErrorFilter, notModifiedFilter, faviconFilter));
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

  static LdapContext activate(String connectionName) throws IOException, NamingException, NoSuchAlgorithmException,
      CertificateException, KeyStoreException, UnrecoverableKeyException {
    var active = ldapContexts.get(connectionName);
    if (active == null) {
      active = Ldap.createLdapContext(connectionName);
      ldapContexts.put(connectionName, active);
    }
    Settings.setActiveConnectionName(connectionName);
    LOGGER.fine(() -> String.format("%s is the active LDAP connection", connectionName));
    return active;
  }

  static LdapContext getActiveLdapContext() throws IOException, NamingException, NoSuchAlgorithmException,
      CertificateException, KeyStoreException, UnrecoverableKeyException {
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
    return Settings.getConnectionSettings(Settings.getActiveConnectionName()).getBaseDn();
  }

  static void delete(String connectionName) throws NamingException {
    var toDelete = ldapContexts.remove(connectionName);
    if (toDelete != null) {
      toDelete.close();
      LOGGER.info(() -> String.format("Deleted %s", connectionName));
    }
    Settings.deleteSingleConnection(connectionName);
    LOGGER.info(String.format("Deleted connection settings for %s", connectionName));
    var activeConnectionName = Settings.getActiveConnectionName();
    if (activeConnectionName.equals(connectionName)) {
      Settings.unsetActiveConnectionName();
    }
  }

  private static void showUsageAndExit(int status) {
    showUsage();
    System.exit(status);
  }

  private static void showUsage() {
    System.err.printf("Usage: %s [port]|[Options]%n", Pike.class.getName());
    System.err.println();
    System.err.println("Serves pages of LDAP entries.");
    System.err.println();
    System.err.println("Arguments:");
    System.err.println();
    System.err
        .println("  port             port the server will listen on (default is " + DEFAULT_HTTP_SERVER_PORT + ")");
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
    var context = httpServer.createContext(path);
    context.getFilters().addAll(filters);
    context.setHandler(handler);
  }

  void serveHttp() {
    LOGGER.info(() -> String.format("%s running with PID %d", Pike.class.getName(), ProcessHandle.current().pid()));
    httpServer.start();
  }

  void shutdown() {
    for (String connectionName : ldapContexts.keySet()) {
      var ldapContext = ldapContexts.get(connectionName);
      if (ldapContext != null) {
        try {
          ldapContext.close();
          LOGGER.info(String.format("Removed LDAP connection %s.", connectionName));
        } catch (NamingException e) {
          LOGGER.log(Level.WARNING, "Problem closing LdapContext on shutdown!", e);
        }
      }
    }
    LOGGER.warning("Stopping HTTP server...");
    httpServer.stop(0);
  }
}
