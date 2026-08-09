#!/usr/bin/env bash
# 从 ~/.m2 收集 ydsz-common-json 编译所需依赖，按文件名定位（含 groupId 目录），输出 Windows 风格 classpath 到 cp.txt
M2="/c/Users/Marvin/.m2/repository"

JARFILES=(
  "slf4j-api-1.7.32.jar"
  "spring-core-5.3.14.jar"
  "spring-jcl-5.3.14.jar"
  "spring-context-5.3.14.jar"
  "spring-beans-5.3.14.jar"
  "spring-aop-5.3.14.jar"
  "spring-expression-5.3.14.jar"
  "spring-boot-2.5.8.jar"
  "spring-boot-autoconfigure-2.5.8.jar"
  "spring-web-5.3.14.jar"
  "jackson-annotations-2.13.3.jar"
  "jackson-core-2.13.3.jar"
  "jackson-databind-2.13.3.jar"
  "fastjson2-2.0.43.jar"
  "jakarta.annotation-api-1.3.5.jar"
  "jakarta.validation-api-2.0.2.jar"
)

CP=""
missing=""
for f in "${JARFILES[@]}"; do
  p=$(find "$M2" -name "$f" 2>/dev/null | grep -v sources | grep -v javadoc | head -1)
  if [ -n "$p" ] && [ -f "$p" ]; then
    wp=$(echo "$p" | sed 's|^/c/|C:/|; s|^/d/|D:/|')
    CP="$CP;$wp"
  else
    missing="$missing $f"
  fi
done

if [ -n "$missing" ]; then
  echo "MISSING:$missing" >&2
fi
CP="${CP#;}"
echo "$CP" > cp.txt
echo "classpath written ($(printf '%s' "$CP" | tr ';' '\n' | wc -l) entries)"
