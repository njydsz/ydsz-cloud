"""
Add missing `import io.swagger.v3.oas.annotations.media.Schema;` to cronjob DTO files.

Files using `@Schema` annotation but missing the import are in:
  - ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/dto/put/
  - ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/dto/post/

The import is inserted after the package declaration (before other imports)
to maintain consistent ordering with sibling files that already have it.
"""
import pathlib
import re

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-cronjob\ydsz-cronjob-domain\src\main\java\com\njydsz\cronjob\domain\dto")
SCHEMA_IMPORT = "import io.swagger.v3.oas.annotations.media.Schema;"

missing_files = [
    ROOT / "put" / "JobPutDTO.java",
    ROOT / "put" / "JobDagPutDTO.java",
    ROOT / "put" / "AlertRulePutDTO.java",
    ROOT / "post" / "JobPostDTO.java",
    ROOT / "post" / "JobDagPostDTO.java",
    ROOT / "post" / "AlertRulePostDTO.java",
]

for f in missing_files:
    if not f.exists():
        print(f"SKIP (not found): {f}")
        continue
    text = f.read_text(encoding="utf-8")
    if SCHEMA_IMPORT in text:
        print(f"SKIP (already has import): {f.name}")
        continue
    # Insert import after package declaration line
    new_text = re.sub(
        r"(package [^;]+;\s*\n)",
        r"\1\n" + SCHEMA_IMPORT + "\n",
        text,
        count=1,
    )
    if new_text == text:
        print(f"FAIL (could not insert): {f.name}")
        continue
    f.write_text(new_text, encoding="utf-8")
    print(f"FIXED: {f.name}")

print("Done.")
