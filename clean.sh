#!/usr/bin/env bash

JAR_FILE=pike.jar

[[ -f $JAR_FILE ]] && rm -f $JAR_FILE
[[ -d bin ]] && rm -rf bin 
