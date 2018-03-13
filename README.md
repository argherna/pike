# pike

An HTTP-based LDAP browser intended for a single-user or a small team working on a software development project that needs to interact with LDAP.

## Building

Run the build script and it'll be built:

    ./build.sh

## Running

Run the jar file:

    java -jar pike.jar [args]

Logging is based on JDK logging and output to the console by default. Press `^C` to stop the server.

Arguments & options are needed to run the server. The arguments and options are similar to `ldapsearch`:

```
Usage: Server [OPTIONS] [port]

Serves pages of LDAP entries.

Arguments:

  port             port the server will listen on (default is 8080)

Options:

  -b <searchbase>  base for searches
  -D <bindDn>      bind DN
  -h, --help       Show this help and exit
  -H <url>         LDAP URL
  -w <password>    Bind password
  -W               Prompt for password
  -Z               Use StartTLS
```

## Viewing Records

URLs can be entered in the browser as follows:

    http://localhost:[port]/dir/record/rdn;[RDN]?filter=[url-encoded filter]&attr=[attribute-name]&scope=[search scope]

The URL uses [matrix parameters](https://www.w3.org/DesignIssues/MatrixURIs.html) to specify the RDN. An example path would be something like `/dir/record/rdn;ou=Users;cn=developers`.

If `filter` isn't specified, the default is `(objectClass=*)`.

If `attr` isn't specified, all attributes are returned. The `attr` parameter can be specified multiple times, for example: `attr=cn&attr=objectClass&attr=mail`.

If `scope` isn't specified or if `scope` isn't a valid value, a subtree search is performed. Valid values of scope are `subtree`, `object`, and `onelevel`.

## Philosophy

This server is being built entirely with the JDK, without external dependencies. This limits what can be done quickly in terms of developer productivity, but increases user productivity in that building and running the software (which is the main goal) is shorter.

## Roadmap

Desired features:

* Ability to connect to different LDAP servers from 1 running instance
* Remember certain information entered on the command line so the user doesn't have to enter it all the time
* Configuration page to manage (locally) some connection information

## Etymology

The name for this project is for [Pike County western in Illinois](https://en.wikipedia.org/wiki/Pike_County,_Illinois). It's also short to type on the command line which makes it efficient to use.