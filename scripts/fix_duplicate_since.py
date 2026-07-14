import os
import re

root = r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-json\src'
pattern = re.compile(r'(@since 1\.3\.0)\n\s*\* @since 1\.3\.0')
count = 0

for dirpath, dirs, files in os.walk(root):
    for filename in files:
        if not filename.endswith('.java'):
            continue
        filepath = os.path.join(dirpath, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        new_content = pattern.sub(r'\1', content)
        if new_content != content:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(new_content)
            count += 1
            print(f'Fixed: {filepath}')

print(f'Total fixed: {count} files')
