#!/bin/sh
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
GRADLE_OPTS="${GRADLE_OPTS:-""}"
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
die() { echo; echo "ERROR: $*"; echo; exit 1; }
warn() { echo "WARNING: $*"; }

# OS specific support
case "$(uname)" in CYGWIN*|MINGW*) cygwin=true;; Darwin*) darwin=true;; MSYS*|MINGW*) msys=true;; esac

# Find JAVA_HOME
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
  [ -x "$JAVACMD" ] || die "Java not found."
else
  JAVACMD="$(command -v java 2>/dev/null)"
  [ -n "$JAVACMD" ] || die "Java not found."
fi

# Determine wrapper jar
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Download gradle-wrapper.jar if missing
if [ ! -f "$CLASSPATH" ]; then
  curl -sL "https://raw.githubusercontent.com/gradle/gradle/v8.6.0/gradle/wrapper/gradle-wrapper.jar" -o "$CLASSPATH" || die "Could not download gradle-wrapper.jar"
fi

exec "$JAVACMD" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
