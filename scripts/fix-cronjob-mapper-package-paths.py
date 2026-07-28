"""
Move cronjob Mapper / Entity / DTO source files to subdirectories that match
their package declarations.

Root cause: Files declared `package com.njydsz.cronjob.infra.mapper.job;` but
were physically stored in `mapper/` (without the `job/` subdirectory). This
caused javac to emit class files into `mapper/` instead of `mapper/job/`,
which broke downstream modules that imported them via the declared package.

Same problem exists for:
  - ydsz-cronjob-infra: mapper/ → mapper/{job,dag,log,schedule}/
  - ydsz-cronjob-domain: entity/ → entity/{job,dag,log,schedule}/
  - ydsz-cronjob-domain: dto/   → dto/{post,put}/ (already correct)

This script:
  1. Scans each .java file in the target directories.
  2. Parses the `package` declaration.
  3. If the package's last segment doesn't match the file's parent directory name,
     moves the file to a subdirectory matching that segment.
  4. Re-runs until no moves are needed.

Idempotent: running it twice is a no-op.
"""
import pathlib
import re
import shutil

INFRA_MAPPER = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-cronjob\ydsz-cronjob-infra\src\main\java\com\njydsz\cronjob\infra\mapper")
DOMAIN_ENTITY = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-cronjob\ydsz-cronjob-domain\src\main\java\com\njydsz\cronjob\domain\entity")

PACKAGE_RE = re.compile(r"^package\s+([\w\.]+);", re.MULTILINE)

def fix_dir(root: pathlib.Path) -> int:
    moved = 0
    for f in list(root.glob("*.java")):
        text = f.read_text(encoding="utf-8")
        m = PACKAGE_RE.search(text)
        if not m:
            continue
        pkg = m.group(1)
        # Last segment of the package is the expected subdirectory name
        expected_subdir = pkg.split(".")[-1]
        current_subdir = f.parent.name
        if expected_subdir == current_subdir:
            continue
        target_dir = root / expected_subdir
        target_dir.mkdir(exist_ok=True)
        target_file = target_dir / f.name
        if target_file.exists():
            print(f"SKIP (target exists): {f} → {target_file}")
            continue
        shutil.move(str(f), str(target_file))
        print(f"MOVED: {f.name} → {expected_subdir}/{f.name}  (package: {pkg})")
        moved += 1
    return moved

total = 0
print("=== Fixing ydsz-cronjob-infra/mapper ===")
total += fix_dir(INFRA_MAPPER)
print("\n=== Fixing ydsz-cronjob-domain/entity ===")
total += fix_dir(DOMAIN_ENTITY)
print(f"\nTotal moves: {total}")
