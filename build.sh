#!/usr/bin/env sh
set -eu
mvn clean package
printf '\nBuilt: target/VupeCore-1.0.0.jar\n'
