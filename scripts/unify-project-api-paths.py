"""统一 project 模块所有 Controller 的 @RequestMapping 路径为 /api/project/** 前缀

执行规则:
  - 仅修改类级 @RequestMapping("/xxx") 注解
  - 在原路径前插入 /api/project 前缀
  - 不修改方法级 @GetMapping/@PostMapping 等注解
  - 不修改已有 /api/ 前缀的注解(理论上 project 模块当前不存在)

 Examples:
  @RequestMapping("/finance/invoice") -> @RequestMapping("/api/project/finance/invoice")
  @RequestMapping("/execution/wbs")   -> @RequestMapping("/api/project/execution/wbs")
  @RequestMapping("/contract")        -> @RequestMapping("/api/project/contract")
"""

from __future__ import annotations

import pathlib
import re

CTRL_DIR = pathlib.Path(
    "ydsz-pmis-backend/ydsz-pmis-project/ydsz-pmis-project-web/src/main/java/com/njydsz/pmis/project/web/controller"
)

# 类级 @RequestMapping 注解匹配(单行)
# 形如:@RequestMapping("/finance/invoice") 或 @RequestMapping(value = "/xxx")
PATTERN_CLASS_MAPPING = re.compile(
    r'^(@RequestMapping\s*\(\s*)(?:"|\047)(/[^"\047]*)(?:"|\047)(\s*\))'
)

# 形如:@RequestMapping(value = "/xxx")
PATTERN_VALUE_MAPPING = re.compile(
    r'^(@RequestMapping\s*\(\s*value\s*=\s*)(?:"|\047)(/[^"\047]*)(?:"|\047)(\s*\))'
)


def transform_line(line: str) -> tuple[str, bool]:
    """变换单行,返回 (新行, 是否修改)."""
    # 跳过已带 /api/ 前缀的(理论上 project 模块当前不存在,但防御性处理)
    m = PATTERN_CLASS_MAPPING.match(line.strip())
    if m:
        prefix, path, suffix = m.group(1), m.group(2), m.group(3)
        if path.startswith("/api/"):
            return line, False
        new_path = "/api/project" + path
        # 保留原行的前导缩进
        indent = line[: len(line) - len(line.lstrip())]
        return f"{indent}{prefix}\"{new_path}\"{suffix}", True

    m = PATTERN_VALUE_MAPPING.match(line.strip())
    if m:
        prefix, path, suffix = m.group(1), m.group(2), m.group(3)
        if path.startswith("/api/"):
            return line, False
        new_path = "/api/project" + path
        indent = line[: len(line) - len(line.lstrip())]
        return f"{indent}{prefix}\"{new_path}\"{suffix}", True

    return line, False


def process_file(file_path: pathlib.Path) -> int:
    """处理单个 Controller 文件,返回修改的行数."""
    content = file_path.read_text(encoding="utf-8")
    lines = content.split("\n")
    new_lines: list[str] = []
    modified_count = 0
    for line in lines:
        new_line, changed = transform_line(line)
        new_lines.append(new_line)
        if changed:
            modified_count += 1

    if modified_count > 0:
        new_content = "\n".join(new_lines)
        file_path.write_text(new_content, encoding="utf-8")
    return modified_count


def main() -> None:
    if not CTRL_DIR.exists():
        raise SystemExit(f"Controller 目录不存在: {CTRL_DIR}")

    controllers = sorted(CTRL_DIR.glob("*Controller.java"))
    print(f"[INFO] 发现 {len(controllers)} 个 Controller 文件")

    total_modified = 0
    file_modified_count = 0
    for ctrl in controllers:
        n = process_file(ctrl)
        if n > 0:
            file_modified_count += 1
            total_modified += n
            print(f"  [OK] {ctrl.name}: 修改 {n} 处")
        else:
            print(f"  [SKIP] {ctrl.name}: 无需修改")

    print(f"\n[DONE] 共修改 {file_modified_count} 个文件,{total_modified} 处 @RequestMapping 路径")


if __name__ == "__main__":
    main()
