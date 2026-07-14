"""Fix duplicate @since tags in Java source files."""
import pathlib
import re

base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-exception\src\main\java')
fixed = 0
for f in base.rglob('*.java'):
    content = f.read_text(encoding='utf-8')
    new_content = re.sub(r' \* @since \S+\n \* \n \* @since (\S+)', r' * @since \1', content)
    if new_content != content:
        f.write_text(new_content, encoding='utf-8')
        fixed += 1
        print(f'Fixed: {f.name}')
print(f'Total fixed: {fixed} files')
