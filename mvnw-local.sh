#!/usr/bin/env bash
# Maven 便捷包装脚本（避免 PATH 中 Maven 失效问题）
exec java -Xms256m -Xmx1g \
  -classpath "D:/Maven/njydsz-maven/boot/plexus-classworlds-2.11.0.jar" \
  "-Dclassworlds.conf=D:/Maven/njydsz-maven/bin/m2.conf" \
  "-Dmaven.home=D:/Maven/njydsz-maven" \
  "-Dlibrary.jansi.path=D:/Maven/njydsz-maven/lib/jansi-native" \
  "-Dmaven.multiModuleProjectDirectory=${PWD}" \
  -Denforcer.skip=true \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
