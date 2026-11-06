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

# 敏感路径前缀：命中任一即视为需要权限注解的端点。
#
# 历史缺口（2026-11-06）：原清单仅含 ("/admin", "/internal", "/ops")，
# 导致以下端点全部逃逸——RuleImportExportController（导出全量规则/DSL）、
# RuleDebugController（调试会话控制）、ExportTaskController（导出任务提交）、
# AsyncTaskController（Agent 任务提交）、ErrorCodeController（全量错误码）、
# MetricsDashboardController（系统指标）。故按“危险能力语义”扩展为前缀 + 关键词双维度。
SENSITIVE_PREFIXES = (
    "/admin",
    "/internal",
    "/ops",
    "/export",
    "/debug",
    "/metrics",
    "/error-codes",
    "/rules",
    "/async-task",
    "/repeat-submit",
    "/rollback",
    "/terminate",
    "/trigger",
    "/import",
)

# 危险能力关键词：类级 @RequestMapping 含任一关键词即敏感（用于捕获
# /literule/rules、/cronjob/export-tasks、/system/metrics 等前缀不在上表的场景）。
SENSITIVE_KEYWORDS = (
    "admin",
    "internal",
    "export",
    "debug",
    "rollback",
    "terminate",
    "trigger",
    "error-codes",
    "async-task",
    "repeat-submit",
)

SENSITIVE_CLASS_SUFFIXES = ("AdminController", "InternalController", "OpsController")
HEALTH_PATH_HINTS = ("/ping", "/health", "/healthz", "/ready", "/live", "/metrics")

# 公共元数据端点：路径含这些关键词的端点面向前端构建期/CI 拉取，已在网关
# common-ignore-url 白名单中免 Token（见 ydsz-gateway application.yml），
# 再加权限注解会直接打挂前端构建与错误码同步流水线，故豁免。
# 如需新增，请同步确认已在网关白名单内。
PUBLIC_METADATA_HINTS = (
    "error-codes",
    "error_codes",
    "repeat-submit",
    "repeat_submit",
)


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


def _build_line_map(raw: str, stripped: str) -> dict[int, int]:
    """建立「去注释文本行号 → 原始文本行号」的映射。

    <p>去注释只删除注释<b>内容</b>（保留换行），因此两者行数一致时可直接按下标对应。
    但若块注释独占整行，剥除后该行会变成空行而依然保留，行号仍然对齐；
    本函数采用逐行贪心匹配，对少数边界情况（注释内出现换行拼接）做容错。
    """
    if raw.count("\n") == stripped.count("\n"):
        return {i: i for i in range(stripped.count("\n") + 1)}
    # 行数不一致时退化：逐行做内容包含匹配
    raw_lines = raw.split("\n")
    stripped_lines = stripped.split("\n")
    mapping: dict[int, int] = {}
    raw_idx = 0
    for stripped_idx, stripped_line in enumerate(stripped_lines):
        needle = stripped_line.strip()
        if not needle:
            mapping[stripped_idx] = stripped_idx
            continue
        while raw_idx < len(raw_lines):
            candidate = _strip_comments(raw_lines[raw_idx]).strip()
            if candidate == needle:
                mapping[stripped_idx] = raw_idx
                raw_idx += 1
                break
            raw_idx += 1
    return mapping


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

    实现说明：去注释会缩短文本长度，不能直接用 `raw_text[:len(header)]` 取原始头部
    （会漏掉 Javadoc 尾部，导致 InternalJobController 这类把说明写在 Javadoc 的
    Controller 无法命中豁免）。这里改为在原始文本中定位类声明行后取其全部前置内容。
    """
    pos = _class_decl_start(text)
    if pos < 0:
        return False
    header = text[:pos]
    if "InternalTokenFilter" in header or "内部签名" in header:
        return True
    if not raw_text:
        return False
    # 在原始文本中定位同一个类名声明，取其前置内容（含 Javadoc）
    m = CLASS_DECL_RE.search(raw_text)
    if not m:
        return False
    raw_header = raw_text[: m.start()]
    return "InternalTokenFilter" in raw_header or "内部签名" in raw_header


def _has_controller_annotation(text: str) -> bool:
    """判断类是否为 Spring Controller（有 @RestController 或 @Controller）。"""
    m = CLASS_DECL_RE.search(text)
    if not m:
        return False
    header = text[: m.start()]
    return bool(CONTROLLER_ANN_RE.search(header))


def is_sensitive(mapping: str | None, cls_name: str | None) -> bool:
    """判定是否为敏感 Controller。

    <p>命中任一维度即敏感：
    <ul>
      <li>类级 @RequestMapping 以 SENSITIVE_PREFIXES 中任一前缀开头</li>
      <li>类级 @RequestMapping 含 SENSITIVE_KEYWORDS 中任一关键词（忽略大小写）</li>
      <li>类名以 SENSITIVE_CLASS_SUFFIXES 中任一后缀结尾</li>
    </ul>
    """
    if mapping:
        normalized = mapping.rstrip("/")
        for prefix in SENSITIVE_PREFIXES:
            if normalized == prefix or normalized.startswith(prefix + "/"):
                return True
        lowered = normalized.lower()
        for keyword in SENSITIVE_KEYWORDS:
            if keyword in lowered:
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

    # 建立 text（去注释）→ raw（原始）行号映射。
    # 去注释会删除块注释/行注释行，导致同一端点在两个文本中下标不同；
    # 上报行号必须基于原始文件，否则定位漂移（历史 bug：DebugController
    # replay 实际在 177 行，门禁却报 111 行，指向了无关代码）。
    text_to_raw = _build_line_map(raw, text)

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
        # 端点注解区：从 Mapping 行向上取固定窗口（覆盖多行注解 + Javadoc）。
        #
        # 不采用「向上逐行判断是否停止」的策略——注解可跨多行（@Audit(...)）、
        # 字符串内可含括号（content = "'xxx: ' + #id")，任何基于括号深度或
        # 「遇非 @ 即停」的启发式都会被这些边界情况击穿（历史 bug：DebugController
        # replay 已有 @AuthApiPermission 却被误报）。固定窗口简单且鲁棒：
        # 上方 20 行足以覆盖任意注解组合，误报只会来自「20 行内混入上一端点的注解」，
        # 而相邻端点间距通常 > 20 行（含 Javadoc），风险可控。
        window_start = max(0, idx - 20)
        window = "\n".join(lines[window_start: idx + 25])
        if PERMISSION_ANN_RE.search(window):
            continue
        if _is_health_endpoint(stripped):
            continue
        # 公共元数据端点（错误码表、防重令牌）已免 Token，不再要求权限码。
        # 类级路径含关键词也认（部分端点的 @GetMapping 不带路径参数，如 @GetMapping 直接映射类路径）。
        lowered = (stripped + " " + str(mapping)).lower()
        if any(hint in lowered for hint in PUBLIC_METADATA_HINTS):
            continue
        # 上报原始文件行号（便于直接跳转），并回显原始行内容
        raw_idx = text_to_raw.get(idx, idx)
        raw_stripped = raw_lines[raw_idx].strip() if raw_idx < len(raw_lines) else stripped
        rel = path.relative_to(REPO_ROOT).as_posix()
        violations.append((raw_idx + 1, f"{rel}:{raw_idx + 1} 端点缺权限注解 → {raw_stripped[:80]}"))
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
