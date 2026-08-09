#!/usr/bin/env bash
# 编译核心子集（排除依赖 Spring 的文件），全部使用 "D:/..." 形式绝对路径
set -u
BASE="D:/Code/open/ydsz-cloud/ydsz-common/ydsz-common-json"
SRC="$BASE/src/main/java"
STUB="$BASE/verify/stub"
OUT="$BASE/verify/classes"
LOG="$BASE/verify/compile.log"
JAVAC="/c/Program Files/Amazon Corretto/jdk21.0.8_9/bin/javac"
CP="C:/Users/Marvin/.m2/repository/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar;C:/Users/Marvin/.m2/repository/org/slf4j/slf4j-simple/2.0.16/slf4j-simple-2.0.16.jar"

rm -rf "$OUT"; mkdir -p "$OUT"

LIST=/tmp/srcs3.txt
# 排除：spring 包、autotype 真实实现、以及其它直接依赖 Spring 的文件
/usr/bin/find "$SRC" -name "*.java" \
  | grep -v "/spring/" \
  | grep -v "DualJsonDetector.java" \
  > "$LIST"
echo "$BASE/verify/JsonHardeningCheck.java" >> "$LIST"

echo "===== 源文件数: $(wc -l < "$LIST") ====="
"$JAVAC" -encoding UTF-8 -nowarn -d "$OUT" -cp "$CP" $(cat "$LIST") > "$LOG" 2>&1
RC=$?
echo "---exit=$RC---"
echo "错误数: $(grep -c '错误:' "$LOG" 2>/dev/null || echo 0)"
echo "class count: $(/usr/bin/find "$OUT" -name '*.class' | wc -l)"
[ $RC -ne 0 ] && grep '错误:' "$LOG" | sed 's|D:.*json/||' | sort -u | head -25
exit $RC
