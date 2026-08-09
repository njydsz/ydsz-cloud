import os, re

ROOT = r"D:/Code/open/ydsz-cloud/ydsz-literule/ydsz-literule-web/src/main/java"
RATE_IMPORT = "import com.njydsz.common.safe.ratelimit.annotation.RateLimit;"
WRITE_MAPPINGS = re.compile(r'@(PostMapping|PutMapping|DeleteMapping|PatchMapping)')

def class_name(path):
    return os.path.splitext(os.path.basename(path))[0]

def camel_to_snake(name):
    s = re.sub(r'Controller$', '', name)
    s = re.sub(r'(?<!^)(?=[A-Z])', '_', s).lower()
    return s

changes = 0
for dirpath, _, files in os.walk(ROOT):
    for fname in files:
        if not fname.endswith('.java'):
            continue
        path = os.path.join(dirpath, fname)
        with open(path, 'r', encoding='utf-8') as fh:
            lines = fh.readlines()
        cls = class_name(fname)
        cls_snake = camel_to_snake(cls)
        has_import = any(RATE_IMPORT in ln for ln in lines)
        need_import = False
        out = []
        i = 0
        file_changed = False
        while i < len(lines):
            line = lines[i]
            m = WRITE_MAPPINGS.search(line)
            if m:
                j = i + 1
                method_name = None
                while j < len(lines) and j <= i + 12:
                    sig = re.search(r'public\s+[\w<>\[\],\s\.]+\s+(\w+)\s*\(', lines[j])
                    if sig:
                        method_name = sig.group(1)
                        break
                    if '@' in lines[j] and 'Mapping' not in lines[j] and j > i + 4:
                        break
                    j += 1
                has_rl = False
                for k in range(max(0, len(out) - 10), len(out)):
                    if '@RateLimit' in out[k]:
                        has_rl = True
                        break
                if not has_rl and method_name:
                    resource = f"literule.{cls_snake}.{method_name}"
                    indent = "    "
                    rl_line = f'{indent}@RateLimit(resource = "{resource}", threshold = 50)\n'
                    out.append(rl_line)
                    need_import = True
                    file_changed = True
                    changes += 1
            out.append(line)
            i += 1
        if file_changed:
            if need_import and not has_import:
                insert_idx = None
                for idx, ln in enumerate(out):
                    if ln.startswith('import '):
                        insert_idx = idx + 1
                    else:
                        break
                if insert_idx is None:
                    insert_idx = 1 if out[0].startswith('package') else 0
                out.insert(insert_idx, RATE_IMPORT + "\n")
            with open(path, 'w', encoding='utf-8') as fh:
                fh.writelines(out)
            print(f"UPDATED: {fname}")
print(f"\nTotal annotations added: {changes}")
