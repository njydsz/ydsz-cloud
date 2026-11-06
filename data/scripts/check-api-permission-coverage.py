#!/usr/bin/env python3
"""API 权限注解覆盖门禁 —— 防止新增业务端点缺失权限注解。

背景：
  check-internal-api-permission.py 只覆盖 /admin /internal /ops 等敏感前缀的
  Controller。但实测发现大量**非敏感路径**的核心业务端点同样零权限注解，
  例如：
    - UserAccountController（/api/user/**）：用户 CRUD、角色分配、启用/禁用、
      密码重置 —— 23 个端点零注解，任意登录用户可直接调用
    - FlowAnalyticsController / FlowTemplateController：流程引擎分析接口
    - RulePackController / RuleGraphController：规则引擎包管理
  这些端点仅靠网关心跳级 JWT 校验（登录态），不校验角色/权限码。

判定方式（棘轮式，只判新增）：
  1. 扫描所有 *Controller.java（src/main）
  2. 对每个 HTTP 端点方法，要求方法上或类上存在
     @AuthApiPermission / @PreAuthorize / @RequireInternal 之一
  3. 白名单（显式豁免，需在 baseline 中写明理由）：
     - 健康检查端点（/ping /health /ready /live /metrics）
     - 认证类公开端点（/login /logout /refresh /codes /captcha /device-code /mfa/send）
     - 已登记在 baseline 文件中的端点（历史存量，只减不增）

baseline 文件：data/api-permission-baseline.json
  格式：{ "<ClassName>#<methodName>": "<豁免理由>" }
  生成：python3 data/scripts/check-api-permission-coverage.py --update-baseline
  规则：baseline 条目数只允许减少；新增未注解端点直接判红。

用法：
  python3 data/scripts/check-api-permission-coverage.py                  # 门禁
  python3 data/scripts/check-api-permission-coverage.py --update-baseline  # 生成基线
  python3 data/scripts/check-api-permission-coverage.py --list           # 列出缺口

退出码非 0 表示存在未授权端点；前提不成立同样失败（fail-closed）。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SKIP_DIRS = frozenset({"target", "backup_logs", "node_modules", "dist", ".git", ".idea"})
BASELINE_PATH = REPO_ROOT / "data" / "api-permission-baseline.json"

HTTP_MAPPING_RE = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping\b')
CONTROLLER_ANN_RE = re.compile(r'@(RestController|Controller)\b')
PERMISSION_ANN_RE = re.compile(
    r'@(?:AuthApiPermission|PreAuthorize|RequireInternal)\b')
CLASS_DECL_RE = re.compile(
    r'^(?:public\s+)?(?:final\s+)?(?:abstract\s+)?class\s+(\w+)', re.MULTILINE)
METHOD_DECL_RE = re.compile(
    r'^\s*(?:public|protected)\s+[\w<>\[\],\s.?]+\s+(\w+)\s*\(', re.MULTILINE)

# 公开端点豁免：这些路径无需权限注解（仅需登录态）
PUBLIC_PATH_HINTS = (
    "/ping", "/health", "/healthz", "/ready", "/live", "/metrics",
    "/login", "/logout", "/register", "/refresh", "/codes", "/captcha",
    "/device-code", "/device-exchange", "/device_authorization", "/mfa/send",
    "/public", "/error", "/actuator",
)


def iter_java_sources(root: Path):
    """遍历 src/main/java 下的 .java 文件。"""
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        norm = dirpath.replace(os.sep, "/")
        if "src/test" in norm or "src/main" not in norm:
            continue
        for name in filenames:
            if name.endswith(".java"):
                yield Path(dirpath) / name


def _strip_comments(text: str) -> str:
    """移除块注释与行注释，避免误判注释里的注解。"""
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.DOTALL)
    text = re.sub(r'//[^\n]*', '', text)
    return text


def _class_header(text: str) -> tuple[int, str]:
    """返回 (类声明偏移, 类声明前的头部文本)。"""
    m = CLASS_DECL_RE.search(text)
    if not m:
        return -1, ""
    return m.start(), text[: m.start()]


def _class_level_permission(text: str) -> bool:
    _, header = _class_header(text)
    return bool(PERMISSION_ANN_RE.search(header))


def _class_level_mapping(text: str) -> str | None:
    _, header = _class_header(text)
    paths = re.findall(r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]+)"', header)
    return paths[-1] if paths else None


def _is_health_or_public(mapping_line: str, full_path: str) -> bool:
    """判定是否为健康检查或公开端点。"""
    for hint in PUBLIC_PATH_HINTS:
        if hint in mapping_line or hint in full_path:
            return True
    return False


def scan_file(path: Path) -> list[dict]:
    """扫描单个 Controller，返回缺失权限注解的端点列表。

    每个元素：{"method": 方法名, "line": 行号, "mapping": 路径}
    """
    try:
        raw = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return []
    text = _strip_comments(raw)
    if not CONTROLLER_ANN_RE.search(text):
        return []

    cls_m = CLASS_DECL_RE.search(text)
    if not cls_m:
        return []
    cls_name = cls_m.group(1)
    cls_has_perm = _class_level_permission(text)
    cls_path = _class_level_mapping(text) or ""

    lines = text.split("\n")
    gaps: list[dict] = []
    for idx, line in enumerate(lines):
        m = HTTP_MAPPING_RE.search(line)
        if not m:
            continue
        # 向下查找该方法的方法级注解块与方法声明
        method_perm = False
        method_name = None
        for j in range(idx, min(idx + 12, len(lines))):
            if PERMISSION_ANN_RE.search(lines[j]):
                method_perm = True
            mm = METHOD_DECL_RE.search(lines[j])
            if mm:
                method_name = mm.group(1)
                break
        if method_perm or cls_has_perm:
            continue
        # 提取路径
        pm = re.search(r'@(?:Get|Post|Put|Delete|Patch)Mapping\(\s*(?:value\s*=\s*)?"([^"]*)"', line)
        ep_path = pm.group(1) if pm else ""
        full_path = (cls_path + ep_path).replace("//", "/")
        if _is_health_or_public(ep_path, full_path):
            continue
        gaps.append({
            "method": method_name or "<unknown>",
            "line": idx + 1,
            "mapping": full_path,
            "key": f"{cls_name}#{method_name or '<unknown>'}",
        })
    return gaps


def scan_all(root: Path) -> list[dict]:
    """扫描全仓，返回缺口列表（含文件路径）。"""
    results: list[dict] = []
    for path in iter_java_sources(root):
        for gap in scan_file(path):
            gap["file"] = str(path.relative_to(root)).replace(os.sep, "/")
            results.append(gap)
    return results


def load_baseline() -> dict:
    if BASELINE_PATH.exists():
        try:
            data = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
            return data.get("exemptions", {})
        except (OSError, json.JSONDecodeError):
            return {}
    return {}


def save_baseline(exemptions: dict) -> None:
    payload = {
        "_comment": (
            "API 权限注解豁免基线：键=ClassName#methodName，值=豁免理由。"
            "只允许减少条目；新增未注解端点会被门禁判红。"
        ),
        "total_exemptions": len(exemptions),
        "exemptions": dict(sorted(exemptions.items())),
    }
    BASELINE_PATH.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="API 权限注解覆盖门禁")
    parser.add_argument("--update-baseline", action="store_true",
                        help="生成/更新豁免基线（用于初始化）")
    parser.add_argument("--list", action="store_true",
                        help="列出所有缺口（含已豁免）")
    args = parser.parse_args()

    gaps = scan_all(REPO_ROOT)
    if not gaps:
        print("OK: 所有业务端点均已具备权限注解。")
        return 0

    # 同一方法可能因重载/多注解重复，按 key 去重
    unique: dict[str, dict] = {}
    for g in gaps:
        unique.setdefault(g["key"], g)
    gaps = list(unique.values())

    if args.update_baseline:
        existing = load_baseline()
        merged = dict(existing)
        for g in gaps:
            merged.setdefault(
                g["key"],
                f"历史存量未注解端点（{g['file']}:{g['line']}，路径 {g['mapping']}），"
                "待补充权限注解后移出基线",
            )
        save_baseline(merged)
        print(f"基线已更新：{BASELINE_PATH}")
        print(f"豁免条目：{len(merged)}（新增 {len(merged) - len(existing)}）")
        return 0

    baseline = load_baseline()
    if not baseline:
        print("ERROR: 基线文件不存在。请先执行 --update-baseline 初始化。",
              file=sys.stderr)
        return 2

    exempt_keys = set(baseline.keys())
    gap_keys = {g["key"] for g in gaps}
    new_gaps = [g for g in gaps if g["key"] not in exempt_keys]
    # 棘轮核心：基线中已被修复（不再出现在缺口集中）的条目应被裁剪
    stale_keys = exempt_keys - gap_keys

    if args.list:
        print(f"缺口总数：{len(gaps)}，已豁免：{len(gaps) - len(new_gaps)}，"
              f"新增未豁免：{len(new_gaps)}，基线可裁剪：{len(stale_keys)}")
        for g in gaps:
            status = "豁免" if g["key"] in exempt_keys else "新增"
            print(f"  [{status}] {g['file']}:{g['line']}  {g['mapping']}  ({g['key']})")
        if stale_keys:
            print("以下基线条目已修复，可从基线移除：")
            for k in sorted(stale_keys):
                print(f"  [已修复] {k}")
        return 0

    if new_gaps:
        print(f"FAIL: 发现 {len(new_gaps)} 个新增未授权端点：", file=sys.stderr)
        for g in new_gaps:
            print(f"  {g['file']}:{g['line']}  {g['mapping']}  ({g['key']})",
                  file=sys.stderr)
        print("\n处理方式：", file=sys.stderr)
        print("  1) 为端点补充 @AuthApiPermission（推荐，参考同类 Controller）", file=sys.stderr)
        print("  2) 若确为公开/自助端点，在类/方法 Javadoc 注明理由后重新 "
              "--update-baseline 登记", file=sys.stderr)
        return 1

    # 裁剪已修复条目（自动棘轮收紧），并保留人工豁免理由
    if stale_keys:
        pruned = {k: v for k, v in baseline.items() if k in gap_keys}
        save_baseline(pruned)
        print(f"已自动裁剪 {len(stale_keys)} 条已修复的基线条目。")
        baseline = pruned
        exempt_keys = set(baseline.keys())

    current = len(exempt_keys)
    print(f"OK: 无新增未授权端点（存量豁免 {current} 个）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
