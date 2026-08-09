#!/usr/bin/env bash
M2WIN="C:\\Users\\Marvin\\.m2\\repository"
CORE_FWD="D:/Code/open/ydsz-cloud/ydsz-common/ydsz-common-core"
COREWIN="D:\\Code\\open\\ydsz-cloud\\ydsz-common\\ydsz-common-core"
WORKWIN="D:\\Code\\open\\ydsz-cloud\\.coreverify"
LOG="$WORKWIN\\build.log"
mkdir -p "$WORKWIN\\classes" "$WORKWIN\\test-classes" "$WORKWIN\\stub-classes"
: > "$LOG"
JAR_BIN=$(command -v jar || echo "jar")

LOMBOK="$M2WIN\\org\\projectlombok\\lombok\\1.18.46\\lombok-1.18.46.jar"
SLF4J="$M2WIN\\org\\slf4j\\slf4j-api\\1.7.32\\slf4j-api-1.7.32.jar"
TTL="$M2WIN\\com\\alibaba\\transmittable-thread-local\\2.14.5\\transmittable-thread-local-2.14.5.jar"
SBOOT="$M2WIN\\org\\springframework\\boot\\spring-boot\\4.1.0\\spring-boot-4.1.0.jar"
SAUTO="$M2WIN\\org\\springframework\\boot\\spring-boot-autoconfigure\\4.1.0\\spring-boot-autoconfigure-4.1.0.jar"
SCONTEXT="$M2WIN\\org\\springframework\\spring-context\\7.0.8\\spring-context-7.0.8.jar"
SCORE="$M2WIN\\org\\springframework\\spring-core\\7.0.8\\spring-core-7.0.8.jar"
SBEANS="$M2WIN\\org\\springframework\\spring-beans\\7.0.8\\spring-beans-7.0.8.jar"
SAOP="$M2WIN\\org\\springframework\\spring-aop\\7.0.8\\spring-aop-7.0.8.jar"
SEXPR="$M2WIN\\org\\springframework\\spring-expression\\7.0.8\\spring-expression-7.0.8.jar"
SJCL="$M2WIN\\org\\springframework\\spring-jcl\\7.0.8\\spring-jcl-7.0.8.jar"
JVAL="$M2WIN\\jakarta\\validation\\jakarta.validation-api\\3.1.1\\jakarta.validation-api-3.1.1.jar"
JSERVLET="$M2WIN\\jakarta\\servlet\\jakarta.servlet-api\\6.1.0\\jakarta.servlet-api-6.1.0.jar"

CP="$LOMBOK;$SLF4J;$TTL;$SBOOT;$SAUTO;$SCONTEXT;$SCORE;$SBEANS;$SAOP;$SEXPR;$SJCL;$JVAL;$JSERVLET"

echo "[Step1] compile stub json annotations" >>"$LOG"
javac -d "$WORKWIN\\stub-classes" \
  "$WORKWIN\\stubsrc\\com\\njydsz\\common\\json\\naming\\PropertyNamingStrategy.java" \
  "$WORKWIN\\stubsrc\\com\\njydsz\\common\\json\\annotation\\JsonClass.java" \
  "$WORKWIN\\stubsrc\\com\\njydsz\\common\\json\\annotation\\JsonInclude.java" \
  "$WORKWIN\\stubsrc\\com\\njydsz\\common\\json\\annotation\\JsonPropertyOrder.java" >>"$LOG" 2>&1
( cd "$WORKWIN\\stub-classes" && "$JAR_BIN" cf "$WORKWIN\\stub.jar" . )
echo "stub.jar bytes: $("$JAR_BIN" is the jar tool)" >>"$LOG"

echo "[Step2] compile core MAIN" >>"$LOG"
MAIN_SRCS=$(find "$CORE_FWD/src/main/java" -name "*.java" | sed 's|/|\\|g')
DEPS_ALL="$CP;$WORKWIN\\stub.jar"
if javac -encoding UTF-8 -proc:full -processorpath "$LOMBOK" \
     -cp "$DEPS_ALL" -d "$WORKWIN\\classes" $MAIN_SRCS >>"$LOG" 2>&1; then
  echo "MAIN COMPILE OK" >>"$LOG"
else
  echo "MAIN COMPILE FAILED" >>"$LOG"; tail -50 "$LOG"; exit 1
fi

echo "[Step3] compile core TEST" >>"$LOG"
JUP_API="$M2WIN\\org\\junit\\jupiter\\junit-jupiter-api\\5.10.3\\junit-jupiter-api-5.10.3.jar"
JUP_ENG="$M2WIN\\org\\junit\\jupiter\\junit-jupiter-engine\\5.10.3\\junit-jupiter-engine-5.10.3.jar"
JPC="$M2WIN\\org\\junit\\platform\\junit-platform-commons\\1.10.3\\junit-platform-commons-1.10.3.jar"
JPE="$M2WIN\\org\\junit\\platform\\junit-platform-engine\\1.10.3\\junit-platform-engine-1.10.3.jar"
OTT="$M2WIN\\org\\opentest4j\\opentest4j\\1.3.0\\opentest4j-1.3.0.jar"
APIG="$M2WIN\\org\\apiguardian\\apiguardian-api\\1.1.2\\apiguardian-api-1.1.2.jar"
TEST_CP="$CP;$WORKWIN\\stub.jar;$WORKWIN\\classes;$JUP_API;$JUP_ENG;$JPC;$JPE;$OTT;$APIG"
TEST_SRCS=$(find "$CORE_FWD/src/test/java" -name "*.java" 2>/dev/null | sed 's|/|\\|g')
if [ -z "$TEST_SRCS" ]; then echo "no test sources" >>"$LOG"; else
  if javac -encoding UTF-8 -proc:full -processorpath "$LOMBOK" \
       -cp "$TEST_CP" -d "$WORKWIN\\test-classes" $TEST_SRCS >>"$LOG" 2>&1; then
    echo "TEST COMPILE OK" >>"$LOG"
  else
    echo "TEST COMPILE FAILED" >>"$LOG"; tail -50 "$LOG"; exit 2
  fi
fi
echo "ALL COMPILE STEPS PASSED" >>"$LOG"
