import pathlib
import re

root = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-json\src\main\java\com\njydsz\common\json')

# Pattern: catch (Exception e) {\s*\n\s*}
pattern = re.compile(r'catch \(Exception e\) \{\s*\n\s*\}', re.MULTILINE)

replacement = 'catch (Exception e) {\n                // 反射操作失败，忽略此路径，回退到默认行为\n            }'

count = 0
for f in root.rglob('*.java'):
    text = f.read_text(encoding='utf-8')
    if pattern.search(text):
        new_text = pattern.sub(replacement, text)
        if new_text != text:
            f.write_text(new_text, encoding='utf-8')
            count += 1
            print(f'Fixed: {f.name}')

print(f'Total files fixed: {count}')
