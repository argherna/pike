# pike

An HTTP-based LDAP browser intended for a single-user or a small team working on a software development project that needs to interact with LDAP.

## Building

Run the build script and it'll be built:

    ./build.sh

## Running

Run the jar file:

    java -jar pike.jar [args]

Logging is based on JDK logging and output to the console by default. Press `^C` to stop the server.

Arguments & options can be supplied on the command line to run the server:

```
Usage: Pike [OPTIONS] [port]

Serves pages of LDAP entries.

Arguments:

  port             port the server will listen on (default is 8085)

Options:

  -h, --help       Show this help and exit
```

When the server is started, it will look for any last used connection and then use it starting you off on the search page. If none is found, you will be directed to the page with all server connections listed where you can pick one to use. If there are no saved connections, you'll be directed to a New Connection page that will let you enter connection settings for you to save, then use.
## Viewing Records
If `filter` isn't specified, the default is `(objectClass=*)`.

If `scope` isn't specified or if `scope` isn't a valid value, a `subtree` search is performed. Valid values of scope are `subtree`, `object`, and `onelevel`.

## Philosophy

This server is being built entirely with the JDK, without external dependencies. This limits what can be done quickly in terms of developer productivity, but increases user productivity in that building and running the software is faster (which is the main goal).

## Roadmap

Desired features:

* Add authentication type and referral handling to server configuration.
* Save searches associated with an LDAP server.
* Add import/export of saved settings and searches.
* Add SASL binds for server authentication.

## Etymology

The name for this project is for [Pike County western in Illinois](https://en.wikipedia.org/wiki/Pike_County,_Illinois). It's also short to type on the command line which makes it efficient to use.