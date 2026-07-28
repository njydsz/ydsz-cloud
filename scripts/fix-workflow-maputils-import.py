"""
Add missing `import com.njydsz.common.util.collection.MapUtils;` to workflow-server
files that use `MapUtils.xxx` but lack the import.
"""
import pathlib
import re

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-workflow\ydsz-workflow-server\src\main\java")
IMPORT = "import com.njydsz.common.util.collection.MapUtils;"
PACKAGE_RE = re.compile(r"(package [^;]+;\s*\n)")
USAGE_RE = re.compile(r"\bMapUtils\.")

fixed = 0
for f in ROOT.rglob("*.java"):
    text = f.read_text(encoding="utf-8")
    if IMPORT in text:
        continue
    if not USAGE_RE.search(text):
        continue
    # Skip files where MapUtils appears only in javadoc {@link} references that
    # we don't want to touch (none in this batch, but be conservative)
    new_text = PACKAGE_RE.sub(r"\1" + IMPORT + "\n", text, count=1)
    if new_text == text:
        print(f"FAIL: {f.name}")
        continue
    f.write_text(new_text, encoding="utf-8")
    print(f"FIXED: {f.relative_to(ROOT)}")
    fixed += 1

print(f"\nTotal: {fixed}")
