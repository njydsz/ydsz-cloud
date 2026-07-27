"""修复 @RateLimit 和 @PostMapping/@PutMapping/@DeleteMapping 之间的空白行"""
import pathlib
import re

PROJECT_ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis")
BACKEND = PROJECT_ROOT / "ydsz-backend"

pattern = re.compile(
    r'(@RateLimit\([^)]+\))\n\n(\s+@(?:Post|Put|Delete)Mapping)',
    re.MULTILINE
)

fixed = 0
for f in BACKEND.rglob("**/controller/**/*Controller.java"):
    if "target" in str(f):
        continue
    content = f.read_text(encoding="utf-8")
    new_content = pattern.sub(r'\1\n\2', content)
    if new_content != content:
        f.write_text(new_content, encoding="utf-8")
        fixed += 1
        print(f"  Fixed: {f.relative_to(PROJECT_ROOT)}")

print(f"\n修复完成: {fixed} 个文件")