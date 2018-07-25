# pike

An HTTP-based LDAP browser intended for a single-user or a small team working on a software development project that needs to browse a directory. Pike is built with Java-10 and requires at least Java-9 to run.

## Building

Set whatever compiler options you like in `javac.opts`, except leave the `-d bin` option alone since that's where the source files are compiled to. Run the build script and it'll be built:

    ./build.sh

## Running

Run the jar file:

    java [system properties] -jar pike.jar [args]

Logging is based on JDK logging and output to the console by default. You can set the logging configuration to use by specifying the `java.util.logging.config.file` system property. Press `^C` to stop the server.

Arguments & options can be supplied on the command line to run the server:

```
Usage: Pike [port]

Serves pages of LDAP entries.

Arguments:

  port             port the server will listen on (default is 8085)

Options:

  -D, --delete-all-connections
                   Deletes all connections
  -d <conn-name>, --delete-connection <conn-name>
                   Delete the connection settings named <conn-name>
  -h, --help       Show this help and exit
  -i <file-name>, --import-connections <file-name>
                   Import connection settings from <file-name>
  -l, --list-connections
                   List connection names and exit
  -X, --export-all-connections
                   Export all connection settings and exit
  -x <conn-name>, --export-connections <conn-name>
                   Export connection named <conn-name> and exit
```

Start the server by specifying none of the options (you can set the port). Setting the port when using an option will have no affect.

When you start the server, you can navigate to `http://localhost:8085/` (or to whatever port you set on startup) after starting the server. It will look for any last used connection and then use it starting you off on the search page. If none is found, you will be directed to the page with all server connections listed where you can pick one to use. If there are no saved connections, you'll be directed to a New Connection page that will let you enter connection settings for you to save, then use.

## Saved Searches

When you're on the search page, you can save your searches by entering information in the fields and then hitting the `Save Search` button. You'll be prompted for a name for the search. You can open a saved search by selecting it from the `Searches` dropdown.

Searches can be updated with new parameters by hitting the `Update Current Search` button. This will save all data in the form fields with the currently used search.

Saved searches can be deleted by hitting the `Delete Search` button.

## Sharing Settings

Pike will let you manage connection settings through the web UI and the command line. 

To download settings from the web UI, go to `http://localhost:8085/connections` and either select `Export All Connections` from the `Actions` dropdown, or select an individual connection from the list and then select `Export Selected`. When exporting all connections, the file name is `pike.prefs.xml`. When exporting a single connection, the file name is `pike-CONN_NAME.prefs.xml`.

To upload settings from the web UI, go to `http://localhost:8085/connections` and select `Upload Connection(s)`. Choose the file you want to upload and hit the `Upload` button.

Connection settings are written to the console (`System.out`) when exporting from the command line. You can redirect this output on most platforms to a file.

Be aware that the passwords are exported as well, but are encrypted when exported. Saved searches will be exported with the connection settings they're associated with.

The format for the connection settings is the standard [Java Preferences XML](http://java.sun.com/dtd/preferences.dtd). It's best to avoid manually editing the XML in the file(s), but removing passwords and changing Bind DNs manually is fine.

## Roadmap

Desired features:

* Add SASL for server authentication.
* Improve error messages in the UI and logs.

## Philosophy

This server is being built entirely with the JDK, without external dependencies. This limits what can be done quickly in terms of developer productivity, but increases user productivity in that building and running the software is faster (which is the main goal).

## Etymology

The name for this project is for [Pike County western in Illinois](https://en.wikipedia.org/wiki/Pike_County,_Illinois). It's also short to type on the command line which makes it efficient to use.
