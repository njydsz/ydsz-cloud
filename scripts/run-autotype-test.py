#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Run AutoTypeCheckerTest directly via JUnit Platform Launcher API.
Bypasses the parent pom's @{argLine} surefire config issue
(JaCoCo disabled but placeholder still in place).
"""
import pathlib
import subprocess
import sys

MODULE_DIR = pathlib.Path(
    r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-json"
)
TARGET_DIR = MODULE_DIR / "target"
TEST_CP_FILE = TARGET_DIR / "test-cp.txt"
TEST_RUNNER_JAVA = TARGET_DIR / "TestRunner.java"

# JUnit Platform Launcher 6.0.3 jar (already in local maven repo)
LAUNCHER_JAR = pathlib.Path(
    r"C:\Users\Marvin\.m2\repository\org\junit\platform"
    r"\junit-platform-launcher\6.0.3\junit-platform-launcher-6.0.3.jar"
)

# Java home (Amazon Corretto 21)
JAVA_HOME = pathlib.Path(r"C:\Program Files\Amazon Corretto\jdk21.0.8_9")
JAVAC = JAVA_HOME / "bin" / "javac.exe"
JAVA = JAVA_HOME / "bin" / "java.exe"


def build_classpath() -> str:
    """Build full classpath: target/classes + target/test-classes + cp from maven."""
    cp_parts = []
    cp_parts.append(str(TARGET_DIR / "classes"))
    cp_parts.append(str(TARGET_DIR / "test-classes"))
    # Read maven dependency classpath (semicolon-separated on Windows)
    maven_cp = TEST_CP_FILE.read_text(encoding="utf-8").strip()
    cp_parts.append(maven_cp)
    # Add launcher jar (not on the maven classpath for runtime, only test scope)
    cp_parts.append(str(LAUNCHER_JAR))
    return ";".join(cp_parts)


def main():
    if not TEST_CP_FILE.exists():
        print(f"[ERROR] {TEST_CP_FILE} not found. Run:")
        print("  mvn -pl ydsz-common/ydsz-common-json dependency:build-classpath "
              "-Dmdep.outputFile=target/test-cp.txt -q")
        return 1

    cp = build_classpath()
    print(f"[INFO] Classpath length: {len(cp)} chars")

    # Compile TestRunner.java
    print(f"[INFO] Compiling {TEST_RUNNER_JAVA.name} ...")
    result = subprocess.run(
        [str(JAVAC), "-cp", cp, "-d", str(TARGET_DIR), str(TEST_RUNNER_JAVA)],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if result.returncode != 0:
        print("[ERROR] Compilation failed:")
        print(result.stderr or "(no stderr)")
        print(result.stdout or "(no stdout)")
        return result.returncode
    print("[INFO] Compilation OK")

    # Run TestRunner (target dir itself on cp so TestRunner.class is found)
    print("[INFO] Running AutoTypeCheckerTest ...")
    run_cp = cp + ";" + str(TARGET_DIR)
    result = subprocess.run(
        [str(JAVA), "-cp", run_cp, "TestRunner"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    print(result.stdout)
    if result.stderr:
        print("--- stderr ---")
        print(result.stderr)
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
