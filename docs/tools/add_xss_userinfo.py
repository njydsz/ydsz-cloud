import os, re

ROOT = r"D:/Code/open/ydsz-cloud/ydsz-userinfo/ydsz-userinfo-domain/src/main/java"
XSS_IMPORT = "import com.njydsz.common.safe.annotation.Xss;"

# 只处理写入类 DTO（Post/Put/Create/ChangePassword/Login 等）
SKIP_PATTERNS = ['password', 'secret', 'token', 'idCard', 'bankCard']
FILES = []
for dirpath, _, files in os.walk(ROOT):
    for fname in files:
        if not fname.endswith('.java'):
            continue
        # 只在 dto 目录
        if '\\dto\\' not in dirpath.replace('/', '\\') and '/dto/' not in dirpath:
            continue
        FILES.append(os.path.join(dirpath, fname))

changes = 0
for path in sorted(FILES):
    with open(path, 'r', encoding='utf-8') as fh:
        content = fh.read()
    lines = content.split('\n')
    has_import = any(XSS_IMPORT in ln for ln in lines)
    modified = False
    out = []
    i = 0
    while i < len(lines):
        ln = lines[i]
        # 检测 private String xxx; 字段
        m = re.match(r'^(\s*)private String (\w+);\s*$', ln)
        if m:
            indent = m.group(1)
            fname2 = m.group(2)
            # 跳过敏感字段
            if any(p in fname2.lower() for p in SKIP_PATTERNS):
                out.append(ln); i += 1; continue
            # 检查上一行是否已有 @Xss
            has_xss = any('@Xss' in lines[j] for j in range(max(0, i-5), i))
            if not has_xss:
                # 在字段上方添加 @Xss（放在字段自己的注释之后，紧跟字段声明）
                out.append(f'{indent}@Xss(message = "{fname2}包含非法内容")\n')
                modified = True
                changes += 1
        out.append(ln)
        i += 1
    if modified:
        if not has_import:
            # 在 package 行后、其他 import 前插入
            insert_idx = 1
            # 找到第一个 import 或空行之后的位置
            for idx, ln in enumerate(out):
                if ln.startswith('import '):
                    insert_idx = idx + 1
                elif ln.startswith('package '):
                    insert_idx = idx + 1
            # 如果插在 package 后没有空行，补空行
            if out[insert_idx-1].startswith('package ') and insert_idx < len(out) and out[insert_idx].strip() != '':
                out.insert(insert_idx, '\n')
                insert_idx += 1
            out.insert(insert_idx, XSS_IMPORT)
            # 确保 import 后有空行分隔
            if insert_idx + 1 < len(out) and out[insert_idx+1].strip() != '':
                out.insert(insert_idx+1, '')
        with open(path, 'w', encoding='utf-8') as fh:
            fh.write('\n'.join(out))
        print(f"UPDATED: {path.replace(ROOT, '')}")

print(f"\nTotal @Xss annotations added: {changes}")
