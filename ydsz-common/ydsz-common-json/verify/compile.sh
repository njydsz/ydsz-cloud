#!/usr/bin/env bash
# 编译核心子集（排除 spring/ 与 autotype/ 真实类，用 stub 替代 AutoTypeChecker）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_POSIX="$SCRIPT_DIR/../src/main/java"
STUB_POSIX="$SCRIPT_DIR/stub"
JAVAC="/c/Program Files/Amazon Corretto/jdk21.0.8_9/bin/javac"
STAMP=$(date +%H%M%S)
OUT="$SCRIPT_DIR/out-$STAMP"
CP=$(cat "$SCRIPT_DIR/cp-win.txt")

mkdir -p "$OUT"

# 收集源文件（排除 spring/ 与 autotype/），转 Windows 路径写入 @file
: > "$SCRIPT_DIR/sources-win.txt"
/usr/bin/find "$SRC_POSIX" -name "*.java" \
  | grep -v "/spring/" \
  | grep -v "/autotype/" \
  | while read -r f; do
      echo "$f" | sed -e 's|^/c/|C:\\|' -e 's|^/d/|D:\\|' -e 's|/|\\|g' >> "$SCRIPT_DIR/sources-win.txt"
    done
echo "$STUB_POSIX/com/njydsz/common/json/autotype/AutoTypeChecker.java" \
  | sed -e 's|^/c/|C:\\|' -e 's|^/d/|D:\\|' -e 's|/|\\|g' >> "$SCRIPT_DIR/sources-win.txt"
echo "$SCRIPT_DIR/JsonHardeningCheck.java" \
  | sed -e 's|^/c/|C:\\|' -e 's|^/d/|D:\\|' -e 's|/|\\|g' >> "$SCRIPT_DIR/sources-win.txt"

echo "===== 源文件数 ====="
wc -l < "$SCRIPT_DIR/sources-win.txt"

echo "===== 编译 -> $OUT ====="
WIN_SCRIPT=$(echo "$SCRIPT_DIR" | sed -E 's|^/([a-z])/|\U\1:\\|; s|/|\\|g')
"$JAVAC" -encoding UTF-8 -d "$OUT" -cp "$CP" @"$WIN_SCRIPT\\sources-win.txt" 2>&1 | head -80
echo "---COMPILE EXIT: ${PIPESTATUS[0]}---"
echo "OUT_DIR=$OUT" > "$SCRIPT_DIR/last-out.txt"
echo "$OUT"