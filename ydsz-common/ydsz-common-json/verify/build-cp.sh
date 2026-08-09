#!/usr/bin/env bash
# 生成 Windows 风格 classpath（供原生 javac.exe 使用），硬编码路径，避免 sed 正则陷阱
REPO="C:/Users/Marvin/.m2/repository"
A="$REPO/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar"
B="$REPO/org/slf4j/slf4j-simple/2.0.16/slf4j-simple-2.0.16.jar"
CP="$A;$B"
echo -n "$CP" > cp-win.txt
echo "classpath: $CP"
