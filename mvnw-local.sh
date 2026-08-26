#!/usr/bin/env bash
JAVA="C:/Program Files/Amazon Corretto/jdk21.0.8_9/bin/java"
MAVEN_HOME='D:/Maven/fresh-maven'
CW='D:/Maven/fresh-maven/boot/plexus-classworlds-2.11.0.jar'
ROOT='D:/Code/open/ydsz-cloud'
"$JAVA" -classpath "$CW" \
  "-Dclassworlds.conf=$MAVEN_HOME\bin\m2.conf" \
  "-Dmaven.home=$MAVEN_HOME" \
  "-Dmaven.multiModuleProjectDirectory=$ROOT" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
