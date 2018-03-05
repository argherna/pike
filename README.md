# pike

An HTTP-based LDAP browser.

## Building

Run the build script and it'll be built:

    ./build.sh

## Running

Run the jar file:

    java -jar pike.jar [args]

Logging is output to the console. Press `^C` to stop the server.

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

    http://localhost:[port]/dir/record/rdn;[RDN]?filter=[url-encoded filter]

The URL uses [matrix parameters](https://www.w3.org/DesignIssues/MatrixURIs.html) to specify the RDN. An example path would be something like `/dir/record/rdn;ou=Users;cn=developers`.

If `filter` isn't specified, the default is `(objectClass=*)`.

## Philosophy

This server is being built entirely with the JDK, without external dependencies. This limits what can be done quickly in terms of developer productivity, but increases user productivity in that building and running the software (which is the main goal) is shorter.

## Roadmap

Desired features:

* Use styled HTML pages to present results
* Present multiple pages to the user with linked content (so users aren't typing long URLs).
* Search page with a text box for entering a filter
* Ability to specify an RDN to the orginal base
* Ability to connect to different LDAP servers from 1 running instance
* Remember certain information entered on the command line so the user doesn't have to enter it all the time
* Configuration page to store (locally) some connection information