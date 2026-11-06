#!/usr/bin/env python3
"""吞异常门禁 —— 防止 Controller 中 catch 块静默吞掉异常（无日志、无抛出）。

背景：
  实测发现 Controller 层存在 catch 后直接 return 默认值、不记录日志的情况，
  导致线上异常无法排查（如 GlobalConcurrencyController.getCurrentConcurrent 返回 -1）。

判定方式：
  1. 扫描 *Controller.java（src/main）中的 catch 块
  2. 提取 catch 块体（花括号匹配）
  3. 若块体内既无 `throw` 也无日志调用（log./LOG./logger.），判为吞异常
  4. 白名单：
     - 块体内含 `// 预期行为` / `// 协议要求` / `// 回退` / `// CAS` 注释（人工确认的合理防御）
     - 块体内含 `// 构建` 等中文注释说明

用法：
  python3 data/scripts/check-swallowed-exception.py [--self-test] [--list]
退出码非 0 表示存在吞异常。
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SKIP_DIRS = frozenset({"target", "backup_logs", "node_modules", "dist", ".git", ".idea"})

CATCH_RE = re.compile(r"catch\s*\(\s*(?:final\s+)?(\w+)\s+\w+\s*\)\s*\{")
LOG_RE = re.compile(r"\b(?:log|LOG|logger|LOGGER)\s*\.\s*(?:info|warn|error|debug|trace)\b")
ASSERT_RE = re.compile(r"\b(?:assert|fail)\s*\(")
# 人工确认的合理防御注释标记
APPROVED_COMMENT_RE = re.compile(r"//[^\n]*(?:预期行为|协议要求|回退|CAS|已记录日志|降级)")


def _extract_block(text: str, start: int) -> tuple[str, int]:
    """从 start（'{' 之后）提取匹配的花括号块体，返回 (块体, 结束位置)。"""
    depth = 1
    i = start
    while i < len(text) and depth > 0:
        ch = text[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
        elif ch == '"':
            # 跳过字符串字面量
            i += 1
            while i < len(text) and text[i] != '"':
                if text[i] == "\\":
                    i += 1
                i += 1
        elif ch == "'":
            i += 1
            while i < len(text) and text[i] != "'":
                if text[i] == "\\":
                    i += 1
                i += 1
        elif text.startswith("//", i):
            while i < len(text) and text[i] != "\n":
                i += 1
        elif text.startswith("/*", i):
            j = text.find("*/", i)
            i = j + 2 if j >= 0 else len(text)
        i += 1
    return text[start : i - 1], i


def iter_controller_files(root: Path):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        p = dirpath.replace(os.sep, "/")
        if "src/test" in p or "src/main" not in p:
            continue
        for name in filenames:
            if name.endswith("Controller.java"):
                yield Path(dirpath) / name


def scan_text(rel_path: str, text: str) -> list[tuple[int, str]]:
    """扫描文本，返回 [(行号, 说明)]。"""
    violations: list[tuple[int, str]] = []
    for m in CATCH_RE.finditer(text):
        block, _ = _extract_block(text, m.end())
        line = text[: m.start()].count("\n") + 1
        if "throw" in block:
            continue
        if LOG_RE.search(block):
            continue
        if ASSERT_RE.search(block):
            continue
        if APPROVED_COMMENT_RE.search(block):
            continue
        snippet = block.strip().split("\n")[0][:70]
        violations.append((line, f"{rel_path}:{line} [catch {m.group(1)}] {snippet}"))
    return violations


def scan_file(path: Path) -> list[tuple[int, str]]:
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return []
    rel = path.relative_to(REPO_ROOT).as_posix()
    return scan_text(rel, text)


def self_test() -> int:
    ok = True

    def case(name: str, cond: bool) -> None:
        nonlocal ok
        if not cond:
            print(f"SELF-TEST FAIL: {name}")
            ok = False

    # 吞异常：判红
    bad = """
    public long getCurrent() {
      try {
        return ops.getLong(KEY);
      } catch (Exception e) {
        return -1;
      }
    }
"""
    r = scan_text("Test.java", bad)
    case("吞异常判红", len(r) == 1)

    # 有日志：放行
    good1 = """
    public long getCurrent() {
      try {
        return ops.getLong(KEY);
      } catch (Exception e) {
        log.warn("读取失败: reason={}", e.getMessage());
        return -1;
      }
    }
"""
    case("有日志放行", len(scan_text("Test.java", good1)) == 0)

    # 有抛出：放行
    good2 = """
    public long getCurrent() {
      try {
        return ops.getLong(KEY);
      } catch (Exception e) {
        throw new BusinessException(e);
      }
    }
"""
    case("有抛出放行", len(scan_text("Test.java", good2)) == 0)

    # 人工确认注释：放行
    good3 = """
    public void range() {
      try {
        start = Long.parseLong(parts[0]);
      } catch (NumberFormatException e) {
        // 预期行为：Range 头格式非法时返回 416（RFC 7233），非异常场景，无需日志
        response.setStatus(416);
        return;
      }
    }
"""
    case("人工确认注释放行", len(scan_text("Test.java", good3)) == 0)

    # 字符串字面量含花括号：不误判
    good4 = """
    public String build() {
      try {
        return "{ok}";
      } catch (Exception e) {
        log.error("build failed: {}", e.getMessage());
        return "{}";
      }
    }
"""
    case("字符串花括号不误判", len(scan_text("Test.java", good4)) == 0)

    if ok:
        print("SELF-TEST OK: 5 个用例全部通过（1 判红 + 4 放行）")
    return 0 if ok else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="吞异常门禁")
    parser.add_argument("--self-test", action="store_true", help="运行内置自测用例")
    parser.add_argument("--list", action="store_true", help="列出所有吞异常")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    violations: list[tuple[int, str]] = []
    for path in iter_controller_files(REPO_ROOT):
        violations.extend(scan_file(path))

    if violations:
        print(f"发现 {len(violations)} 处吞异常：")
        for _, msg in violations:
            print(f"  - {msg}")
        print(
            "\n修复方式（二选一）：\n"
            "  1. 补日志：log.warn(\"...: reason={}\", e.getMessage());\n"
             "  2. 补抛出：throw new BusinessException(...);\n"
            "  3. 若为合理防御（协议要求/预期分支），加注释标记：// 预期行为 / // 协议要求 / // 回退"
        )
        return 1

    print("OK: 未发现吞异常。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
