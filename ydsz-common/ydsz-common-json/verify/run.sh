#!/usr/bin/env bash
# 运行硬化验证 harness
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
POSIX_OUT=$(cat "$SCRIPT_DIR/last-out.txt" | sed 's|OUT_DIR=||')
if command -v cygpath >/dev/null 2>&1; then
  WIN_OUT=$(cygpath -w "$POSIX_OUT")
else
  WIN_OUT=$(echo "$POSIX_OUT" | sed -e 's|^/c/|C:\\|' -e 's|^/d/|D:\\|' -e 's|/|\\|g')
fi
SLF4J="C:/Users/Marvin/.m2/repository/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar;C:/Users/Marvin/.m2/repository/org/slf4j/slf4j-simple/2.0.16/slf4j-simple-2.0.16.jar"
WIN_CP="$WIN_OUT;$SLF4J"
echo "WIN_CP=$WIN_CP"
"/c/Program Files/Amazon Corretto/jdk21.0.8_9/bin/java" -cp "$WIN_CP" verify.JsonHardeningCheck
echo "---RUN EXIT: $?---"
