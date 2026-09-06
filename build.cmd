@echo off
cd /d D:\Code\open\ydsz-cloud
"D:\Maven\njydsz-maven\bin\mvn.cmd" -DskipTests=true --update-snapshots -s "D:\Maven\njydsz-maven\conf\settings.xml" -Dmaven.repo.local="D:\Maven\njydsz-repo" clean install -f pom.xml -rf :ydsz-common-tenant -e
