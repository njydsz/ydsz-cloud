"""统一前端 project 模块相关 API 路径为 /api/project/** 前缀(精确版 v2)

只替换真正的 API 请求 URL,不误伤前端路由跳转。

替换范围:
  1. src/api/**/*.ts - 所有 API 模块文件(全是 API 请求 URL)
  2. src/mock/handlers/*.ts - mock 处理器(用于匹配 API 请求)
  3. src/composables/useCircuitBreaker.ts - 明确是 API 调用示例

不替换:
  - src/router/** - 前端路由定义
  - src/views/** - 视图组件(可能含 router.push)
  - src/components/** - 公共组件
  - src/layout/** - 布局组件
  - 其他可能含路由跳转的文件
"""

from __future__ import annotations

import pathlib
import re

# 只处理这些目录/文件
API_DIRS = [
    pathlib.Path("ydsz-pmis-frontend/src/api"),
    pathlib.Path("ydsz-pmis-frontend/src/mock/handlers"),
]
EXTRA_FILES = [
    pathlib.Path("ydsz-pmis-frontend/src/composables/useCircuitBreaker.ts"),
]

# project 模块所有 Controller 的原路径前缀
PREFIXES = [
    "/afterSales",
    "/alertDispatch",
    "/aggregate",
    "/importExport",
    "/dashboard",
    "/initiation",
    "/execution",
    "/opportunity",
    "/contract",
    "/resource",
    "/closure",
    "/finance",
    "/sales",
    "/report",
]


def build_pattern(prefix: str) -> re.Pattern[str]:
    """匹配 'PREFIX 或 "PREFIX 或 `PREFIX,后跟非字母字符."""
    escaped = re.escape(prefix)
    return re.compile(rf'(["\047`])({escaped})(?![A-Za-z0-9_])')


PATTERNS = [(prefix, build_pattern(prefix)) for prefix in PREFIXES]

# /search 特殊处理:必须后跟 / 或引号
SEARCH_PATTERN = re.compile(r'(["\047`])(/search)(?=[/"\047`])')


def transform_content(content: str) -> tuple[str, int]:
    """变换文件内容,返回 (新内容, 修改次数)."""
    modified_count = 0
    new_content = content

    for prefix, pattern in PATTERNS:
        def repl(m: re.Match[str]) -> str:
            nonlocal modified_count
            modified_count += 1
            quote = m.group(1)
            return f"{quote}/api/project{prefix}"

        new_content = pattern.sub(repl, new_content)

    def repl_search(m: re.Match[str]) -> str:
        nonlocal modified_count
        modified_count += 1
        quote = m.group(1)
        return f"{quote}/api/project/search"

    new_content = SEARCH_PATTERN.sub(repl_search, new_content)

    return new_content, modified_count


def process_file(file_path: pathlib.Path) -> int:
    """处理单个文件,返回修改次数."""
    try:
        content = file_path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, PermissionError):
        return 0

    new_content, n = transform_content(content)
    if n > 0:
        file_path.write_text(new_content, encoding="utf-8")
    return n


def collect_files() -> list[pathlib.Path]:
    """收集所有待处理的文件."""
    files: list[pathlib.Path] = []
    for d in API_DIRS:
        if d.exists():
            files.extend(d.rglob("*.ts"))
    for f in EXTRA_FILES:
        if f.exists():
            files.append(f)
    return files


def main() -> None:
    files = collect_files()
    print(f"[INFO] 待处理 {len(files)} 个文件(仅 src/api + src/mock/handlers + 指定 composables)")

    total_modified = 0
    file_modified_count = 0
    for f in sorted(files):
        n = process_file(f)
        if n > 0:
            file_modified_count += 1
            total_modified += n
            rel = f.relative_to(pathlib.Path("ydsz-pmis-frontend"))
            print(f"  [OK] {rel}: {n} 处")

    print(f"\n[DONE] 共修改 {file_modified_count} 个文件,{total_modified} 处路径")


if __name__ == "__main__":
    main()
