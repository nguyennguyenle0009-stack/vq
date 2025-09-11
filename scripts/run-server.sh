#!/usr/bin/env bash
set -e
JAVA_OPTS="-Xms256m -Xmx512m"
VQ_LOG_DIR="$HOME/Desktop/Vuong quyen/server"
mkdir -p "$VQ_LOG_DIR"
exec java $JAVA_OPTS -DVQ_LOG_DIR="$VQ_LOG_DIR" -jar "$(dirname "$0")/../server/build/libs/server-all.jar"
