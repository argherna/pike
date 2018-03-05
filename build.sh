#!/usr/bin/env bash

JAR_FILE=pike.jar
MAIN=Server

[[ -f $BIN ]] && rm -f $BIN
[[ -d bin ]] && rm -rf bin 
[[ ! -d bin ]] && mkdir -p bin

SOURCES=$(mktemp sourcefiles.XXXX)
find src -type f -name *.java > $SOURCES

# Compile the program.
#
javac -d bin @$SOURCES

[[ $? -eq 0 ]] && jar cef $MAIN $JAR_FILE -C bin .

rm -f $SOURCES
