#!/usr/bin/env bash
M2WIN="C:\\Users\\Marvin\\.m2\\repository"
WORKWIN="D:\\Code\\open\\ydsz-cloud\\.coreverify"
CORE_FWD="D:/Code/open/ydsz-cloud/ydsz-common/ydsz-common-core"
RUNLOG="$WORKWIN\\run.log"
: > "$RUNLOG"

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

JUP_API6="$M2WIN\\org\\junit\\jupiter\\junit-jupiter-api\\6.0.3\\junit-jupiter-api-6.0.3.jar"
JUP_API5="$M2WIN\\org\\junit\\jupiter\\junit-jupiter-api\\5.10.3\\junit-jupiter-api-5.10.3.jar"
JUP_ENG="$M2WIN\\org\\junit\\jupiter\\junit-jupiter-engine\\6.0.3\\junit-jupiter-engine-6.0.3.jar"
JPC="$M2WIN\\org\\junit\\platform\\junit-platform-commons\\6.0.3\\junit-platform-commons-6.0.3.jar"
JPE="$M2WIN\\org\\junit\\platform\\junit-platform-engine\\6.0.3\\junit-platform-engine-6.0.3.jar"
JPL="$M2WIN\\org\\junit\\platform\\junit-platform-launcher\\6.0.3\\junit-platform-launcher-6.0.3.jar"
OTT="$M2WIN\\org\\opentest4j\\opentest4j\\1.3.0\\opentest4j-1.3.0.jar"
APIG="$M2WIN\\org\\apiguardian\\apiguardian-api\\1.1.2\\apiguardian-api-1.1.2.jar"

CP="$LOMBOK;$SLF4J;$TTL;$SBOOT;$SAUTO;$SCONTEXT;$SCORE;$SBEANS;$SAOP;$SEXPR;$SJCL;$JVAL;$JSERVLET"
RUNCP="$CP;$WORKWIN\\stub.jar;$WORKWIN\\classes;$WORKWIN\\test-classes;$JUP_API6;$JUP_API5;$JUP_ENG;$JPC;$JPE;$JPL;$OTT;$APIG"

echo "[compile runner]" >>"$RUNLOG"
javac -encoding UTF-8 -cp "$RUNCP" -d "$WORKWIN\\runner" "$WORKWIN\\runner\\Runner.java" >>"$RUNLOG" 2>&1
echo "[run tests]" >>"$RUNLOG"
java -cp "$RUNCP;$WORKWIN\\runner" Runner >>"$RUNLOG" 2>&1
echo "EXIT=$?" >>"$RUNLOG"
