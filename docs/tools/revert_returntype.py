import os, re, glob

# 修正误改的返回类型：
# - 方法体内调用 PageResponse.success/empty -> 保持 PageResponse<T>
# - 方法体内未调用 PageResponse -> 恢复为 BaseResponse<List<T>>

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

ret_pat = re.compile(r'public\s+PageResponse<([\w.]+)>\s+(\w+)\s*\(')

def find_body(lines, i):
    body_start = None
    for j in range(i, min(i + 15, len(lines))):
        if '{' in lines[j]:
            body_start = j
            break
    if body_start is None:
        return None
    depth = 0
    for j in range(body_start, len(lines)):
        depth += lines[j].count('{') - lines[j].count('}')
        if depth <= 0:
            return (body_start, j)
    return None

total_fix = 0
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
            m = ret_pat.search(ln)
            if m:
                inner = m.group(1)
                method = m.group(2)
                body_range = find_body(lines, i)
                if body_range:
                    body = ''.join(lines[body_range[0]:body_range[1] + 1])
                    uses_pageresult = 'PageResponse.success' in body or 'PageResponse.empty' in body
                    if not uses_pageresult:
                        # 误改：恢复为 BaseResponse<List<T>>
                        fixed = ln.replace(f'PageResponse<{inner}>', f'BaseResponse<List<{inner}>>', 1)
                        new_lines.append(fixed)
                        changed = True
                        i += 1
                        continue
            new_lines.append(ln)
            i += 1
        if changed:
            with open(path, "w", encoding="utf-8") as fh:
                fh.writelines(new_lines)
            total_fix += 1
            print(f"REVERTED: {os.path.basename(path)}")
print(f"Total files reverted: {total_fix}")
