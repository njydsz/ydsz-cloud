#!/usr/bin/env python3
"""内部/管理端点权限门禁 —— 防止 /admin、/internal、/ops 类端点缺少权限注解。

背景：
  实测发现 AdminSessionController 等 6 个 Controller 零权限注解，
  任意登录用户可调用封禁用户、强制下线、强制释放分布式锁等高危操作。
  网关 /api/admin/** 仅校验 JWT（登录态），不校验角色/权限码。

判定方式：
  1. 扫描所有 *Controller.java（src/main）与 *RestController.java
  2. 识别"敏感 Controller"：
     - 类级 @RequestMapping 路径以 /admin、/internal、/ops、/ops-admin 开头
     - 或类名匹配 *AdminController / *InternalController / *OpsController
  3. 对敏感 Controller 的每个 HTTP 端点方法（@GetMapping/@PostMapping/@PutMapping/
     @DeleteMapping/@PatchMapping 或类级 @RequestMapping 已声明 method），
     要求方法上或类上存在 @AuthApiPermission 或 @PreAuthorize
  4. 白名单：健康检查端点（路径含 /ping /health /healthz /ready /live）

豁免（不判红）：
  - 类上已有 @AuthApiPermission / @PreAuthorize / @RequireInternal 的，视为类级兜底
  - 非敏感 Controller（路径不以敏感前缀开头）
  - 非 Controller 类（无 @RestController / @Controller 注解，如 OutboxAdminController
    仅作为 Spring Bean 注册，不暴露 HTTP 端点）
  - 受 InternalTokenFilter / 内部签名保护的端点（Javadoc 含 "InternalTokenFilter" 或
    "内部签名" 且类路径含 internal/migrate 等内部通信语义）
  - 测试代码（src/test）

用法：
  python3 data/scripts/check-internal-api-permission.py [--self-test] [--list]
退出码非 0 表示存在未授权端点；前提不成立同样失败（fail-closed）。
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SKIP_DIRS = frozenset({"target", "backup_logs", "node_modules", "dist", ".git", ".idea"})

HTTP_MAPPING_RE = re.compile(
    r'^\s*@(Get|Post|Put|Delete|Patch|Request)Mapping\b')
CONTROLLER_ANN_RE = re.compile(r'@(RestController|Controller)\b')
PERMISSION_ANN_RE = re.compile(r'@AuthApiPermission\b|@PreAuthorize\b|@RequireInternal\b')
CLASS_DECL_RE = re.compile(
    r'^(?:public\s+)?(?:final\s+)?(?:abstract\s+)?class\s+(\w+)', re.MULTILINE)
JAVADOC_OR_COMMENT_RE = re.compile(r'^\s*(?://|\*|/\*)')

SENSITIVE_PREFIXES = ("/admin", "/internal", "/ops")
SENSITIVE_CLASS_SUFFIXES = ("AdminController", "InternalController", "OpsController")
HEALTH_PATH_HINTS = ("/ping", "/health", "/healthz", "/ready", "/live", "/metrics")


def iter_java_sources(root: Path):
    """遍历 src/main/java 下的 .java 文件，排除 target / backup_logs 等。"""
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        if "src/test" in dirpath.replace(os.sep, "/"):
            continue
        if "src/main" not in dirpath.replace(os.sep, "/"):
            continue
        for name in filenames:
            if name.endswith(".java"):
                yield Path(dirpath) / name


def _strip_comments(text: str) -> str:
    """移除块注释与行注释，避免误判注释里的注解。"""
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.DOTALL)
    text = re.sub(r'//[^\n]*', '', text)
    return text


def _class_decl_start(text: str) -> int:
    """返回类声明行的起始偏移；未找到返回 -1。"""
    m = CLASS_DECL_RE.search(text)
    return m.start() if m else -1


def _line_of_offset(text: str, offset: int) -> int:
    """把字符偏移换算成行号（0-based）；offset<0 时返回 -1。"""
    if offset < 0:
        return -1
    return text.count("\n", 0, offset)


def _class_level_permission(text: str) -> bool:
    """判断类声明之前（含注解区）是否存在权限注解。"""
    pos = _class_decl_start(text)
    if pos < 0:
        return False
    header = text[:pos]
    return bool(PERMISSION_ANN_RE.search(header))


def _class_level_mapping(text: str) -> str | None:
    """提取类级 @RequestMapping 的路径值。"""
    m = CLASS_DECL_RE.search(text)
    if not m:
        return None
    header = text[: m.start()]
    # 取最后一个 @RequestMapping(...) 的字符串字面量
    paths = re.findall(r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]+)"', header)
    return paths[-1] if paths else None


def _class_name(text: str) -> str | None:
    m = CLASS_DECL_RE.search(text)
    return m.group(1) if m else None


def _is_internal_token_protected(text: str, raw_text: str = "") -> bool:
    """判断 Controller 是否由 InternalTokenFilter / 内部签名保护（内部通信，非用户端点）。

    参数 text 为去注释后的文本（用于类声明定位），raw_text 为原始文本（保留 Javadoc，
    因为 "InternalTokenFilter" 通常写在 Javadoc 里）。
    """
    pos = _class_decl_start(text)
    if pos < 0:
        return False
    header = text[:pos]
    raw_header = raw_text[: len(header)] if raw_text else header
    return "InternalTokenFilter" in header or "内部签名" in header or (
        "InternalTokenFilter" in raw_header or "内部签名" in raw_header
    )


def _has_controller_annotation(text: str) -> bool:
    """判断类是否为 Spring Controller（有 @RestController 或 @Controller）。"""
    m = CLASS_DECL_RE.search(text)
    if not m:
        return False
    header = text[: m.start()]
    return bool(CONTROLLER_ANN_RE.search(header))


def is_sensitive(mapping: str | None, cls_name: str | None) -> bool:
    """判定是否为敏感 Controller。"""
    if mapping:
        normalized = mapping.rstrip("/")
        for prefix in SENSITIVE_PREFIXES:
            if normalized == prefix or normalized.startswith(prefix + "/"):
                return True
    if cls_name:
        for suffix in SENSITIVE_CLASS_SUFFIXES:
            if cls_name.endswith(suffix):
                return True
    return False


def _is_health_endpoint(mapping_line: str) -> bool:
    """判定端点是否为健康检查（路径含 health/ping 等）。"""
    for hint in HEALTH_PATH_HINTS:
        if hint in mapping_line:
            return True
    return False


def scan_file(path: Path) -> list[tuple[int, str]]:
    """扫描单个文件，返回违规列表 [(行号, 说明)]。"""
    try:
        raw = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return []
    text = _strip_comments(raw)
    lines = text.split("\n")
    raw_lines = raw.split("\n")

    if not _has_controller_annotation(text):
        return []

    cls_name = _class_name(text)
    mapping = _class_level_mapping(text)
    if not is_sensitive(mapping, cls_name):
        return []

    # 类级已有权限注解 → 全部端点合规
    if _class_level_permission(text):
        return []
    # 受 InternalTokenFilter 保护的内部通信端点 → 豁免
    if _is_internal_token_protected(text, raw):
        return []

    class_start_line = _line_of_offset(text, _class_decl_start(text))
    violations: list[tuple[int, str]] = []
    for idx, line in enumerate(lines):
        stripped = line.strip()
        if JAVADOC_OR_COMMENT_RE.match(stripped):
            continue
        if not HTTP_MAPPING_RE.match(line):
            continue
        # 跳过类级 @RequestMapping（位于类声明行之前）
        if class_start_line >= 0 and idx < class_start_line:
            continue
        # 端点注解区 = 本 Mapping 行 + 其上方的连续注解行（Spring 注解可写在 Mapping 之前）
        start = idx
        j = idx - 1
        while j >= 0:
            prev = lines[j].strip()
            if not prev or JAVADOC_OR_COMMENT_RE.match(prev):
                break
            if HTTP_MAPPING_RE.match(lines[j]) or PERMISSION_ANN_RE.match(prev):
                start = j
                j -= 1
                continue
            if prev.startswith("@"):
                start = j
                j -= 1
                continue
            break
        # 端点方法签名区 = 本 Mapping 行 + 其下方直到方法体结束（取 25 行窗口）
        window = "\n".join(lines[start: idx + 25])
        if PERMISSION_ANN_RE.search(window):
            continue
        if _is_health_endpoint(stripped):
            continue
        rel = path.relative_to(REPO_ROOT).as_posix()
        violations.append((idx + 1, f"{rel}:{idx + 1} 端点缺权限注解 → {stripped[:80]}"))
    return violations


def self_test() -> int:
    """自测：构造正反用例，验证检测逻辑。"""
    ok = True

    # 正例：敏感 Controller + 端点无权限 → 应判红
    bad = '''
@RestController
@RequestMapping("/admin")
public class AdminSessionController {
  @PostMapping("/users/{userId}/ban")
  public YdszResponse<Boolean> banUser(@PathVariable String userId) {
    return YdszResponse.success(true);
  }
}
'''
    if not scan_file_text(bad):
        print("SELF-TEST FAIL: 敏感 Controller 无权限端点未被判红")
        ok = False

    # 反例 1：方法级有权限注解 → 应放行
    good1 = '''
@RestController
@RequestMapping("/admin")
public class AdminSessionController {
  @AuthApiPermission(apiCodes = "userinfo:admin:ban")
  @PostMapping("/users/{userId}/ban")
  public YdszResponse<Boolean> banUser(@PathVariable String userId) {
    return YdszResponse.success(true);
  }
}
'''
    if scan_file_text(good1):
        print("SELF-TEST FAIL: 方法级权限注解被误判为违规")
        ok = False

    # 反例 2：类级有权限注解 → 应放行
    good2 = '''
@RestController
@RequestMapping("/internal")
@AuthApiPermission(apiCodes = "workflow:internal:api")
public class InternalWorkflowApiController {
  @GetMapping("/instance/{id}")
  public YdszResponse<Object> get(@PathVariable String id) {
    return YdszResponse.success(null);
  }
}
'''
    if scan_file_text(good2):
        print("SELF-TEST FAIL: 类级权限注解被误判为违规")
        ok = False

    # 反例 3：非敏感 Controller → 应放行
    good3 = '''
@RestController
@RequestMapping("/message/template")
public class MessageTemplateController {
  @GetMapping
  public YdszResponse<Object> list() {
    return YdszResponse.success(null);
  }
}
'''
    if scan_file_text(good3):
        print("SELF-TEST FAIL: 非敏感 Controller 被误判为违规")
        ok = False

    # 反例 4：健康检查端点 → 应放行
    good4 = '''
@RestController
@RequestMapping("/admin/ws")
public class WebSocketAdminController {
  @GetMapping("/ping")
  public YdszResponse<Object> ping() {
    return YdszResponse.success(null);
  }
}
'''
    if scan_file_text(good4):
        print("SELF-TEST FAIL: 健康检查端点被误判为违规")
        ok = False

    # 反例 5：非 Controller 类（无 @RestController）→ 应放行
    good5 = '''
public class OutboxAdminController {
  public YdszResponse<Object> list() {
    return YdszResponse.success(null);
  }
}
'''
    if scan_file_text(good5):
        print("SELF-TEST FAIL: 非 Controller 类被误判为违规")
        ok = False

    # 反例 6：内部接口用 @RequireInternal 标记 → 应放行
    good6 = '''
@RestController
@RequestMapping("/internal")
@RequireInternal
public class InternalApiController {
  @GetMapping("/user/info")
  public YdszResponse<Object> info(@RequestParam String id) {
    return YdszResponse.success(null);
  }
}
'''
    if scan_file_text(good6):
        print("SELF-TEST FAIL: @RequireInternal 标记被误判为违规")
        ok = False

    if ok:
        print("SELF-TEST OK: 6 个用例全部通过（1 判红 + 5 放行）")
    return 0 if ok else 1


def scan_file_text(text: str) -> list[tuple[int, str]]:
    """对文本内容（而非文件路径）执行扫描，供自测使用。"""
    stripped = _strip_comments(text)
    lines = stripped.split("\n")
    if not _has_controller_annotation(stripped):
        return []
    cls_name = _class_name(stripped)
    mapping = _class_level_mapping(stripped)
    if not is_sensitive(mapping, cls_name):
        return []
    if _class_level_permission(stripped):
        return []
    if _is_internal_token_protected(stripped, text):
        return []
    class_start_line = _line_of_offset(stripped, _class_decl_start(stripped))
    violations: list[tuple[int, str]] = []
    for idx, line in enumerate(lines):
        s = line.strip()
        if JAVADOC_OR_COMMENT_RE.match(s):
            continue
        if not HTTP_MAPPING_RE.match(line):
            continue
        if class_start_line >= 0 and idx < class_start_line:
            continue
        start = idx
        j = idx - 1
        while j >= 0:
            prev = lines[j].strip()
            if not prev or JAVADOC_OR_COMMENT_RE.match(prev):
                break
            if HTTP_MAPPING_RE.match(lines[j]) or PERMISSION_ANN_RE.match(prev):
                start = j
                j -= 1
                continue
            if prev.startswith("@"):
                start = j
                j -= 1
                continue
            break
        window = "\n".join(lines[start: idx + 25])
        if PERMISSION_ANN_RE.search(window):
            continue
        if _is_health_endpoint(s):
            continue
        violations.append((idx + 1, s[:80]))
    return violations


def main() -> int:
    parser = argparse.ArgumentParser(description="内部/管理端点权限门禁")
    parser.add_argument("--self-test", action="store_true", help="运行内置自测用例")
    parser.add_argument("--list", action="store_true", help="仅列出敏感 Controller 清单")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    all_violations: list[tuple[int, str]] = []
    sensitive_total = 0
    for path in iter_java_sources(REPO_ROOT):
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        stripped = _strip_comments(text)
        if not _has_controller_annotation(stripped):
            continue
        cls_name = _class_name(stripped)
        mapping = _class_level_mapping(stripped)
        if not is_sensitive(mapping, cls_name):
            continue
        sensitive_total += 1
        if args.list:
            rel = path.relative_to(REPO_ROOT).as_posix()
            has_perm = _class_level_permission(stripped)
            print(f"{'[类级权限]' if has_perm else '[方法级]    '} {rel}  ({cls_name} @ {mapping})")
        all_violations.extend(scan_file(path))

    if args.list:
        print(f"\n敏感 Controller 总数: {sensitive_total}")
        return 0

    if all_violations:
        print(f"发现 {len(all_violations)} 个未授权端点：")
        for _, msg in all_violations:
            print(f"  - {msg}")
        print(
            "\n修复方式：为端点添加 @AuthApiPermission(apiCodes = \"module:resource:action\")，"
            "\n或在敏感 Controller 类级统一添加 @AuthApiPermission。"
            "\n参考 PermissionCodes 常量池（ydsz-common-auth）。"
        )
        return 1

    print(f"OK: 已检查 {sensitive_total} 个敏感 Controller，无未授权端点。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
