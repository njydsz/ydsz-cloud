import re
from pathlib import Path

root = Path(__file__).parent / "ydsz-system" / "ydsz-system-web" / "src" / "main" / "java" / "com" / "njydsz" / "system" / "web" / "controller"

pattern = re.compile(r"^\s*@Idempotent\(key\s*=\s*'[^']*'.*\)\s*\n", re.MULTILINE)

for f in root.glob("*.java"):
    content = f.read_text(encoding="utf-8")
    new_content, count = pattern.subn("", content)
    if count:
        f.write_text(new_content, encoding="utf-8")
        print(f"Removed {count} malformed @Idempotent annotation(s) from {f.name}")
