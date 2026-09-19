#!/bin/sh

# Gradle wrapper script

APP_HOME=$( cd "${0%"${0##*/}"}." > /dev/null && pwd -P )
APP_BASE_NAME=${0##*/}

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi

exec "$JAVACMD" \
    -Xmx512m \
    -classpath "$CLASSPATH" \
    org.gradle.launcher.GradleMain \
    "$@"
