"""统一前端所有 project 模块相关 API 路径为 /api/project/** 前缀

扫描 ydsz-pmis-frontend/src 下所有 .ts/.vue 文件,将形如:
  '/finance/xxx', '/sales/xxx', '/contract/xxx', '/opportunity/xxx',
  '/execution/xxx', '/resource/xxx', '/afterSales/xxx', '/initiation/xxx',
  '/report/xxx', '/dashboard/xxx', '/alertDispatch/xxx', '/aggregate/xxx',
  '/closure/xxx', '/importExport/xxx', '/search/xxx'

的字符串字面量替换为 '/api/project/xxx'。

注意:
  - 仅替换字符串字面量中以这些前缀开头的 URL
  - 不替换注释中提到的路径(除非在字符串内)
  - 不替换已经是 /api/ 开头的路径
  - /search 比较敏感,只替换 /search 后紧跟 / 或 ' 或 " 的,避免误伤 /searchEngine 等
"""

from __future__ import annotations

import pathlib
import re

FRONTEND_DIR = pathlib.Path("ydsz-pmis-frontend/src")

# project 模块所有 Controller 的原路径前缀(按长度降序匹配,避免短前缀误伤长前缀)
# 不包含:/search(单独处理)
PREFIXES = [
    # 长 prefix 优先
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

# 构建正则:匹配 'PREFIX(后面紧跟 / 或引号或结尾)'
# 用 lookbehind 确保前面是引号,lookahead 确保后面是 / 或引号
# 形如: "/finance/xxx", '/finance/xxx', `/finance/xxx`
# 注意:`/finance` 单独出现(无后续 /)也需要替换,但要避免 `/financeManager` 等被误伤
# 所以使用 (?!a-zA-Z) lookahead
def build_pattern(prefix: str) -> re.Pattern[str]:
    # 匹配 "/prefix 或 '/prefix 或 `/prefix,后跟非字母字符
    # 用 lookbehind 限定引号开头,避免匹配 URL 中间片段
    escaped = re.escape(prefix)
    # 引号 + prefix + (后跟非字母,即 / ' " ` 或字符串结尾)
    return re.compile(rf'(["\047`])({escaped})(?![A-Za-z0-9_])')


PATTERNS = [(prefix, build_pattern(prefix)) for prefix in PREFIXES]

# /search 特殊处理:必须后跟 / 或引号,避免误伤 /searchEngine, /searchBar 等
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

    # /search 单独处理(用 lookahead 避免误伤)
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


def main() -> None:
    if not FRONTEND_DIR.exists():
        raise SystemExit(f"前端目录不存在: {FRONTEND_DIR}")

    # 扫描所有 .ts 和 .vue 文件
    files: list[pathlib.Path] = []
    for ext in ("*.ts", "*.vue", "*.tsx"):
        files.extend(FRONTEND_DIR.rglob(ext))

    print(f"[INFO] 扫描 {len(files)} 个 .ts/.vue/.tsx 文件")

    total_modified = 0
    file_modified_count = 0
    for f in sorted(files):
        n = process_file(f)
        if n > 0:
            file_modified_count += 1
            total_modified += n
            rel = f.relative_to(FRONTEND_DIR.parent)
            print(f"  [OK] {rel}: {n} 处")

    print(f"\n[DONE] 共修改 {file_modified_count} 个文件,{total_modified} 处路径")


if __name__ == "__main__":
    main()
