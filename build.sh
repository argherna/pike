#!/usr/bin/env bash

JAR_FILE=pike.jar
MAIN=Pike

# Force cleanup of old builds.
#
[[ -f $JAR_FILE ]] && rm -f $JAR_FILE
[[ -d bin ]] && rm -rf bin 
[[ ! -d bin ]] && mkdir -p bin

# List all source files into a temporary file for easy compliation.
#
SOURCES=$(mktemp sourcefiles.XXXX)
find src -type f -name *.java > $SOURCES

# Compile the program.
#
javac @javac.opts @$SOURCES

# Package as a jar file.
#
[[ $? -eq 0 ]] && jar cef $MAIN $JAR_FILE \
  -C bin . \
  -C resources .

# Clean up temporaries.
#
rm -f $SOURCES
