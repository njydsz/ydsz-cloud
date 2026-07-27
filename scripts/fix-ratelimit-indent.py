"""修复 @RateLimit 后 @PostMapping/@PutMapping/@DeleteMapping 缩进丢失"""
import pathlib
import re

PROJECT_ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis")
BACKEND = PROJECT_ROOT / "ydsz-backend"

pattern = re.compile(
    r'(@RateLimit\([^)]+\)\n)(@(?:Post|Put|Delete)Mapping)',
    re.MULTILINE
)

fixed = 0
for f in BACKEND.rglob("**/controller/**/*Controller.java"):
    content = f.read_text(encoding="utf-8")
    
    def replacer(match):
        ratelimit = match.group(1)
        mapping = match.group(2)
        indent_match = re.match(r'(\s+)@', ratelimit)
        indent = indent_match.group(1) if indent_match else '    '
        return ratelimit + indent + mapping
    
    new_content = pattern.sub(replacer, content)
    if new_content != content:
        f.write_text(new_content, encoding="utf-8")
        fixed += 1
        print(f"  Fixed: {f.relative_to(PROJECT_ROOT)}")

print(f"\n修复完成: {fixed} 个文件")