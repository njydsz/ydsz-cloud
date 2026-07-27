import shutil
import subprocess
import sys
from pathlib import Path

MVN = shutil.which("mvn") or shutil.which("mvn.cmd")
if not MVN:
    print("mvn not found in PATH", file=sys.stderr)
    sys.exit(2)
print(f"Using maven: {MVN}")

services = [
    "ydsz-gateway",
    "ydsz-system",
    "ydsz-project",
    "ydsz-cronjob",
    "ydsz-workflow",
    "ydsz-literule",
    "ydsz-message",
    "ydsz-userinfo",
    "ydsz-nextwiki",
    "ydsz-agent",
]
root = Path(__file__).parent
failures = []
for svc in services:
    pom = root / svc / "pom.xml"
    if not pom.exists():
        print(f"SKIP {svc}: pom.xml not found")
        continue
    print(f"=== compiling {svc} ===", flush=True)
    proc = subprocess.run(
        [MVN, "-f", str(pom), "compile", "-Dmaven.test.skip=true", "-q"],
        cwd=root,
        text=True,
        capture_output=True,
        encoding="utf-8",
    )
    if proc.returncode != 0:
        failures.append((svc, proc.stdout, proc.stderr))
        print(f"FAIL {svc}")
    else:
        print(f"OK {svc}")

log_dir = root / "compile-logs"
log_dir.mkdir(exist_ok=True)

if failures:
    print("\n\n========== FAILURES ==========")
    for svc, stdout, stderr in failures:
        out = (stderr or "") + (stdout or "")
        log_file = log_dir / f"{svc}.log"
        log_file.write_text(out, encoding="utf-8")
        print(f"\n>>> {svc} <<< (full log: {log_file})")
        # Print last 2000 chars to keep output manageable
        print(out[-2000:])
    sys.exit(1)
else:
    print("\nAll services compiled successfully.")
