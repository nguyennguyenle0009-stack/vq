#!/usr/bin/env bash
set -e
JAVA_OPTS="-Xms256m -Xmx512m"
exec java $JAVA_OPTS -jar "$(dirname "$0")/../server/build/libs/server-all.jar"
