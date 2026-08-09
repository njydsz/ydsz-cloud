import os, re, glob

# 精确修复：方法体内调用 PageResult.success/empty 的方法，
# 返回类型应为 PageResult<T>（而非 BaseResponse<List<T>> 或 BaseResponse<PageResult<T>>）

ROOTS = [
    r"D:/Code/open/ydsz-cloud/ydsz-message",
    r"D:/Code/open/ydsz-cloud/ydsz-cronjob",
    r"D:/Code/open/ydsz-cloud/ydsz-system",
    r"D:/Code/open/ydsz-cloud/ydsz-userinfo",
    r"D:/Code/open/ydsz-cloud/ydsz-literule",
    r"D:/Code/open/ydsz-cloud/ydsz-workflow",
    r"D:/Code/open/ydsz-cloud/ydsz-nextwiki",
    r"D:/Code/open/ydsz-cloud/ydsz-agent",
    r"D:/Code/open/ydsz-cloud/ydsz-gateway",
    r"D:/Code/open/ydsz-cloud/ydsz-common",
]

# 匹配返回类型（含 @ 注解之间的 public 声明）
RET_PAT = re.compile(r'public\s+(BaseResponse<List<([\w.]+)>>|BaseResponse<PageResult<([\w.]+)>>)\s+(\w+)\s*\(')

def find_body(lines, i):
    for j in range(i, min(i + 15, len(lines))):
        if '{' in lines[j]:
            start = j
            depth = 0
            for k in range(start, len(lines)):
                depth += lines[k].count('{') - lines[k].count('}')
                if depth <= 0:
                    return (start, k)
            return None
    return None

total = 0
for base in ROOTS:
    for path in glob.glob(base + "/**/*.java", recursive=True):
        if "/test/" in path:
            continue
        with open(path, "r", encoding="utf-8") as fh:
            lines = fh.readlines()
        changed = False
        new_lines = []
        i = 0
        while i < len(lines):
            ln = lines[i]
            m = RET_PAT.search(ln)
            if m:
                inner = m.group(2) or m.group(3)
                method = m.group(4)
                body_range = find_body(lines, i)
                if body_range:
                    body = ''.join(lines[body_range[0]:body_range[1] + 1])
                    if 'PageResult.success' in body or 'PageResult.empty' in body:
                        # 改为 PageResult<T>
                        fixed = re.sub(r'public\s+BaseResponse<(List<[\w.]+>|PageResult<[\w.]+>)>\s+(\w+)\s*\(',
                                       rf'public PageResult<{inner}> \2(', ln)
                        new_lines.append(fixed)
                        changed = True
                        i += 1
                        continue
            new_lines.append(ln)
            i += 1
        if changed:
            with open(path, "w", encoding="utf-8") as fh:
                fh.writelines(new_lines)
            total += 1
            print(f"FIXED: {os.path.basename(path)}")
print(f"Total files fixed: {total}")
