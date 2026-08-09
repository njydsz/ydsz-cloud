import os, re, glob

# 迁移 BaseResponse.successPage(...) / BaseResponse.emptyPage(...) 调用
# 到 PageResult.success(...) / PageResult.empty(...)
# 同时将方法返回类型 BaseResponse<List<T>> 改为 PageResult<T>

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

# 1. BaseResponse.successPage(  -> PageResult.success(
# 2. BaseResponse.emptyPage(  -> PageResult.empty(
# 3. 返回类型 BaseResponse<List<X>> -> PageResult<X>（仅当方法体内使用 PageResult.success）
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
        content, n1 = P1.subn('PageResult.success(', content)
        content, n2 = P2.subn('PageResult.empty(', content)
        # 确保 import PageResult
        if (n1 + n2) > 0:
            if 'import com.njydsz.common.core.response.PageResult;' not in content:
                # 在 BaseResponse import 后添加
                content = content.replace(
                    'import com.njydsz.common.core.response.BaseResponse;',
                    'import com.njydsz.common.core.response.BaseResponse;\nimport com.njydsz.common.core.response.PageResult;')
        if content != orig:
            total_p += (n1 + n2)
            total_files += 1
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(content)
            print(f"UPDATED({n1 + n2}): {os.path.basename(path)}")
print(f"Total replacements: {total_p}, files: {total_files}")
