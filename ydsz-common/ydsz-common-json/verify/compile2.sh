#!/usr/bin/env bash
# 编译核心子集：源文件直接作为 Windows 路径参数传给 javac（绕过 @file 解析问题）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_POSIX="$SCRIPT_DIR/../src/main/java"
STUB_POSIX="$SCRIPT_DIR/stub"
JAVAC="/c/Program Files/Amazon Corretto/jdk21.0.8_9/bin/javac"
STAMP=$(date +%H%M%S)
OUT="$SCRIPT_DIR/out-$STAMP"
CP=$(cat "$SCRIPT_DIR/cp-win.txt")
mkdir -p "$OUT"

# 收集 Windows 路径列表（每行一个），排除 spring/ autotype/
>/tmp/srcs.txt
/usr/bin/find "$SRC_POSIX" -name "*.java" \
  | grep -v "/spring/" | grep -v "/autotype/" \
  | while read -r f; do echo "$f" | sed -e 's|^/c/|C:\\|' -e 's|^/d/|D:\\|' -e 's|/|\\|g'; done >> /tmp/srcs.txt
echo "$SCRIPT_DIR/JsonHardeningCheck.java" | sed -e 's|^/c/|C:\\|' -e 's|^/d/|D:\\|' -e 's|/|\\|g' >> /tmp/srcs.txt

# 拼成单行空格分隔
SRCS=$(paste -sd " " /tmp/srcs.txt)
echo "===== 编译 -> $OUT (源文件数见 /tmp/srcs.txt) ====="
"$JAVAC" -encoding UTF-8 -d "$OUT" -cp "$CP" $SRCS
echo "---COMPILE EXIT: $?---"
echo "class count: $(find "$OUT" -name '*.class' | wc -l)"
echo "OUT_DIR=$OUT" > "$SCRIPT_DIR/last-out.txt"
echo "$OUT"
