import os, re

ROOT = r"D:/Code/open/ydsz-cloud/ydsz-literule/ydsz-literule-web/src/main/java"
RATE_IMPORT = "import com.njydsz.common.safe.ratelimit.annotation.RateLimit;"

fixed = 0
for dirpath, _, files in os.walk(ROOT):
    for fname in files:
        if not fname.endswith('.java'):
            continue
        path = os.path.join(dirpath, fname)
        with open(path, 'r', encoding='utf-8') as fh:
            lines = fh.readlines()
        # 如果 import 在 package 声明后立即出现（第2行），且第2行后没有空行
        if len(lines) >= 2 and lines[0].startswith('package ') and RATE_IMPORT in lines[1]:
            # 移除第2行的 import
            removed = lines.pop(1)
            # 找到最后一个普通 import 的位置（在所有 import 行之后），在 package 行后找
            insert_idx = None
            for idx, ln in enumerate(lines):
                if ln.startswith('import ') and 'ratelimit' not in ln:
                    insert_idx = idx + 1
            # 确保在最后一个非 ratelimit import 后插入，且保持空行分隔
            if insert_idx is None:
                # 没有其他 import，插到 package 后空一行处
                insert_idx = 1
                if lines[insert_idx].strip() != '':
                    lines.insert(insert_idx, '\n')
                    insert_idx += 1
                lines.insert(insert_idx, RATE_IMPORT + '\n')
            else:
                lines.insert(insert_idx, RATE_IMPORT + '\n')
            with open(path, 'w', encoding='utf-8') as fh:
                fh.writelines(lines)
            fixed += 1
            print(f"FIXED: {fname}")
print(f"Total import positions fixed: {fixed}")
