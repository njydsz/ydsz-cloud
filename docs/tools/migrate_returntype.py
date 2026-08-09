import os, re, glob

# 将 Controller 方法返回类型 BaseResponse<List<X>> 改为 PageResponse<X>
# 仅当方法体内使用 PageResponse.success/empty 时

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

# 匹配 public BaseResponse<List<X>> methodName(
RET = re.compile(r'public\s+BaseResponse<List<([\w.]+)>>\s+(\w+)\s*\(')

total = 0
for base in ROOTS:
    for path in glob.glob(base + "/**/*.java", recursive=True):
        if "/test/" in path:
            continue
        with open(path, "r", encoding="utf-8") as fh:
            content = fh.read()
        orig = content
        content, n = RET.subn(r'public PageResponse<\1> \2(', content)
        if content != orig:
            total += n
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(content)
            print(f"UPDATED({n}): {os.path.basename(path)}")
print(f"Total return type changes: {total}")
