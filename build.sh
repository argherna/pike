#!/usr/bin/env bash

JAR_FILE=pike.jar
MAIN=Pike

[[ -f $JAR_FILE ]] && rm -f $JAR_FILE
[[ -d bin ]] && rm -rf bin 
[[ ! -d bin ]] && mkdir -p bin

SOURCES=$(mktemp sourcefiles.XXXX)
find src -type f -name *.java > $SOURCES

# Compile the program.
#
javac -Xdiags:verbose --release 9 -d bin @$SOURCES

[[ $? -eq 0 ]] && jar cef $MAIN $JAR_FILE \
  -C bin . \
  -C resources .

rm -f $SOURCES
