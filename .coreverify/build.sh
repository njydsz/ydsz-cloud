#!/usr/bin/env bash
set -e
M2="$HOME/.m2/repository"
CORE="D:/Code/open/ydsz-cloud/ydsz-common/ydsz-common-core"
WORK="D:/Code/open/ydsz-cloud/.coreverify"
rm -rf "$WORK/classes" "$WORK/test-classes" "$WORK/stub-classes" "$WORK/stub.jar"
mkdir -p "$WORK/classes" "$WORK/test-classes" "$WORK/stub-classes"

LOMBOK="$M2/org/projectlombok/lombok/1.18.46/lombok-1.18.46.jar"
SLF4J="$M2/org/slf4j/slf4j-api/1.7.32/slf4j-api-1.7.32.jar"
TTL="$M2/com/alibaba/transmittable-thread-local/2.14.5/transmittable-thread-local-2.14.5.jar"
SBOOT="$M2/org/springframework/boot/spring-boot/2.5.8/spring-boot-2.5.8.jar"
SAUTO="$M2/org/springframework/boot/spring-boot-autoconfigure/2.5.8/spring-boot-autoconfigure-2.5.8.jar"
SCONTEXT="$M2/org/springframework/spring-context/5.3.14/spring-context-5.3.14.jar"
SCORE="$M2/org/springframework/spring-core/5.3.14/spring-core-5.3.14.jar"
SBEANS="$M2/org/springframework/spring-beans/5.3.14/spring-beans-5.3.14.jar"
SAOP="$M2/org/springframework/spring-aop/5.3.14/spring-aop-5.3.14.jar"
JVAL="$M2/jakarta/validation/jakarta.validation-api/2.0.2/jakarta.validation-api-2.0.2.jar"
JSERVLET="$M2/jakarta/servlet/jakarta.servlet-api/6.0.0/jakarta.servlet-api-6.0.0.jar"

CP="$LOMBOK:$SLF4J:$TTL:$SBOOT:$SAUTO:$SCONTEXT:$SCORE:$SBEANS:$SAOP:$JVAL:$JSERVLET"

echo "=== Step 1: compile stub json annotations ==="
javac -d "$WORK/stub-classes" \
  "$WORK/stubsrc/com/njydsz/common/json/naming/PropertyNamingStrategy.java" \
  "$WORK/stubsrc/com/njydsz/common/json/annotation/JsonClass.java" \
  "$WORK/stubsrc/com/njydsz/common/json/annotation/JsonInclude.java" \
  "$WORK/stubsrc/com/njydsz/common/json/annotation/JsonPropertyOrder.java"
( cd "$WORK/stub-classes" && jar cf "$WORK/stub.jar" . )
echo "stub.jar built: $(ls -la "$WORK/stub.jar" | awk '{print $5}') bytes"

echo "=== Step 2: compile core MAIN ==="
MAIN_SRCS=$(find "$CORE/src/main/java" -name "*.java")
DEPS_ALL="$CP:$WORK/stub.jar"
if javac -encoding UTF-8 -proc:full -processorpath "$LOMBOK" \
     -cp "$DEPS_ALL" -d "$WORK/classes" $MAIN_SRCS 2>"$WORK/main_err.txt"; then
  echo "MAIN COMPILE OK"
else
  echo "MAIN COMPILE FAILED"; cat "$WORK/main_err.txt"; exit 1
fi

echo "=== Step 3: compile core TEST ==="
JUP_API="$M2/org/junit/jupiter/junit-jupiter-api/5.10.3/junit-jupiter-api-5.10.3.jar"
JUP_ENG="$M2/org/junit/jupiter/junit-jupiter-engine/5.10.3/junit-jupiter-engine-5.10.3.jar"
JPC="$M2/org/junit/platform/junit-platform-commons/1.10.3/junit-platform-commons-1.10.3.jar"
JPE="$M2/org/junit/platform/junit-platform-engine/1.10.3/junit-platform-engine-1.10.3.jar"
OTT="$M2/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar"
APIG="$M2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar"
TEST_CP="$CP:$WORK/stub.jar:$WORK/classes:$JUP_API:$JUP_ENG:$JPC:$JPE:$OTT:$APIG"
TEST_SRCS=$(find "$CORE/src/test/java" -name "*.java")
if [ -z "$TEST_SRCS" ]; then echo "no test sources"; else
  if javac -encoding UTF-8 -proc:full -processorpath "$LOMBOK" \
       -cp "$TEST_CP" -d "$WORK/test-classes" $TEST_SRCS 2>"$WORK/test_err.txt"; then
    echo "TEST COMPILE OK"
  else
    echo "TEST COMPILE FAILED"; cat "$WORK/test_err.txt"; exit 2
  fi
fi
echo "ALL COMPILE STEPS PASSED"
