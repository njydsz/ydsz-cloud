import os, re, glob

# 迁移 BaseResponse.successPage(...) / BaseResponse.emptyPage(...) 调用
# 到 PageResponse.success(...) / PageResponse.empty(...)
# 同时将方法返回类型 BaseResponse<List<T>> 改为 PageResponse<T>

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

# 1. BaseResponse.successPage(  -> PageResponse.success(
# 2. BaseResponse.emptyPage(  -> PageResponse.empty(
# 3. 返回类型 BaseResponse<List<X>> -> PageResponse<X>（仅当方法体内使用 PageResponse.success）
P1 = re.compile(r'BaseResponse\.successPage\(')
P2 = re.compile(r'BaseResponse\.emptyPage\(')

total_p = 0
total_files = 0
for base in ROOTS:
    for path in glob.glob(base + "/**/*.java", recursive=True):
        if "/test/" in path:
            continue
        with open(path, "r", encoding="utf-8") as fh:
            content = fh.read()
        orig = content
        content, n1 = P1.subn('PageResponse.success(', content)
        content, n2 = P2.subn('PageResponse.empty(', content)
        # 确保 import PageResponse
        if (n1 + n2) > 0:
            if 'import com.njydsz.common.core.response.PageResponse;' not in content:
                # 在 BaseResponse import 后添加
                content = content.replace(
                    'import com.njydsz.common.core.response.BaseResponse;',
                    'import com.njydsz.common.core.response.BaseResponse;\nimport com.njydsz.common.core.response.PageResponse;')
        if content != orig:
            total_p += (n1 + n2)
            total_files += 1
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(content)
            print(f"UPDATED({n1 + n2}): {os.path.basename(path)}")
print(f"Total replacements: {total_p}, files: {total_files}")
