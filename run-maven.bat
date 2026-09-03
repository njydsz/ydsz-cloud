@echo off
cd /d D:\Code\open\ydsz-cloud
"D:\Maven\njydsz-maven\bin\mvn.cmd" -DskipTests=true -Dcheckstyle.skip=true -s "D:\Maven\njydsz-maven\conf\settings.xml" -Dmaven.repo.local="D:\Maven\njydsz-repo" clean install -rf :ydsz-common-util -f pom.xml > maven-build.log 2>&1
echo Exit code: %ERRORLEVEL%
