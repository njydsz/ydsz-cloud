import os
import sys

SRC_DIR = r"d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-json\src"

count = 0
for root, dirs, files in os.walk(SRC_DIR):
    for fname in files:
        if fname.endswith('.java') or fname.endswith('.imports'):
            filepath = os.path.join(root, fname)
            with open(filepath, 'r', encoding='utf-8-sig') as f:
                content = f.read()
            # Re-write without BOM
            with open(filepath, 'w', encoding='utf-8', newline='') as f:
                f.write(content)
            count += 1

print(f"Stripped BOM from {count} files")
