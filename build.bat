@echo off
"D:/Maven/njydsz-maven/bin/mvn.cmd" -DskipTests=true --update-snapshots -s "D:/Maven/njydsz-maven/conf/settings.xml" -Dmaven.repo.local="D:/Maven/njydsz-repo" install -rf :ydsz-literule-web -f pom.xml > D:/Code/open/ydsz-cloud/build.log 2>&1
