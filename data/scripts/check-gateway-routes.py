#!/usr/bin/env python3
"""网关路由一致性门禁 — 断言「路由清单 / Controller 实际路径 / 服务注册名 / 端口 / 前端代理」互相咬合。

门禁对象不是"文件是否存在"，而是接线是否成立。历史上这几条同时坏过且互相掩盖：
  - routes-nacos.yaml 与 application.yml 各写一份路由，前缀归属漂移（/api/permission/**、
    /api/internal/** 曾被路由到没有对应 Controller 的服务）；
  - /api/notify/** /api/job/** /api/wiki/** 之类路由指向根本不存在的 Controller（死路由）；
  - nginx 与 vite 代理先把 /api 剥掉，网关又 StripPrefix=1，等于剥两次 → 全量 404；
  - 前端把 /api/v1/... 写进 baseURL 相对路径，拼出 /api/api/v1/...，而 P0-A2 已改为
    Header 协商版本，网关侧根本没有 /api/v1/** 路由；
  - nginx 新增的透传 location（如 /ws/）在网关侧无人认领，请求进网关即 404 且无人报错。

用法：
  python3 data/scripts/check-gateway-routes.py
  python3 data/scripts/check-gateway-routes.py --frontend-root ../ydsz-micro
  python3 data/scripts/check-gateway-routes.py --self-test    # 反例夹具自检
退出码非 0 表示存在缺陷；所有规则 fail-closed（读不到必要文件即失败，不静默跳过）。
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
GATEWAY_RES = REPO_ROOT / "ydsz-gateway" / "src" / "main" / "resources"
AUTOCONFIG_IMPORTS = (
    GATEWAY_RES
    / "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
)

# 非 @RequestMapping 承载的路由（静态资源/第三方控制台），逐条写明理由；新增前缀不得进这里
ROUTE_ALLOWLIST = {
    "ydsz-cronjob-console": "/cronjob/*.html 由 cronjob 模块的静态资源处理器提供，无 Controller",
}

# 路由归属争议：一条路由抢下了另一个服务也实现的前缀（或子路径）。
# 网关只能把前缀给一个服务，被抢的一侧经网关必然不可达 —— 改路由解决不了，
# 必须有一侧改名。棘轮规则：新出现的抢注一律判红；已登记条目降级为告警。
KNOWN_ROUTE_SHADOWING = {
    "ydsz-system-secondary-auth": (
        "ydsz-system 的 SecondaryAuthController(@RequestMapping \"/auth/secondary-auth\") 与 "
        "ydsz-userinfo 的 AuthController(@PostMapping \"/secondary-auth\" 等 3 个端点) 同名；"
        "路由现归 ydsz-system，userinfo 侧 secondary-auth / secondary-auth/webauthn[/challenge] "
        "经网关不可达。待决策：一侧改名（如 /auth/2fa/**）后删除本条 —— 见报告 B0-9 未决项"
    ),
}

# 后端存在、但网关有意不路由的路径（历史别名/内部专用）。同样只减不增。
KNOWN_UNROUTED_PATHS = {
    "/v1/monitor/sourcemaps": (
        "MonitorReportController 的双路径别名（与 /monitor/sourcemaps 同一实现）。"
        "P0-A2 已改为 X-Api-Version 头协商，网关不路由 /api/v1/**；"
        "规范路径 /api/monitor/sourcemaps 可用，调用方应改用它"
    ),
}

# nginx 原样透传给网关、但路由清单无人认领的前缀。只减不增，认领后立即删除本条。
KNOWN_UNROUTED_PROXY_PREFIXES = {
    "/ws": (
        "6 个服务经 ydsz-common-socket 把 STOMP 端点注册在同一个 /ws"
        "（ydsz.websocket.endpoint 默认值），网关按路径无法判定归属；"
        "且这些服务的 dev 配置 ydsz.websocket.enabled=false，实时链路本就未启用。"
        "待决策：各服务改用 /ws/{service} 端点后按服务加路由 —— 见报告 B0-9 未决项"
    ),
}

MAPPING_ANNOTATION_RE = re.compile(r"@(?:Request|Get|Post|Put|Delete|Patch)Mapping\b")
QUOTED_RE = re.compile(r'"([^"]*)"')
NAMED_ATTR_RE = re.compile(r'^\s*(\w+)\s*=\s*(.*)$', re.DOTALL)
PATH_ATTR_NAMES = frozenset({"value", "path"})
CLASS_DECL_RE = re.compile(r"^\s*(?:public\s+|final\s+|abstract\s+)*class\s+\w", re.MULTILINE)
CONTROLLER_MARKER_RE = re.compile(r"@RestController|@Controller\b")
PLACEHOLDER_DEFAULT_RE = re.compile(r"^\$\{[^:}]+:([^}]+)\}$")


class GateError(RuntimeError):
    """门禁前提不成立（必要文件缺失或不可解析）。"""


# ===========================================================================
# 数据模型
# ===========================================================================


@dataclass
class Endpoint:
    """一个后端可访问路径（已剥掉网关 /api 命名空间）。"""

    service: str
    path: str
    source: str


@dataclass
class Route:
    """路由清单里的一条 RouteDefinition（简化模型）。"""

    route_id: str
    service: str
    patterns: list[str]
    strip_prefix: bool
    order: int
    index: int


@dataclass
class Findings:
    """判定结果收集器。"""

    failures: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def fail(self, message: str) -> None:
        self.failures.append(message)

    def warn(self, message: str) -> None:
        self.warnings.append(message)

    def extend(self, messages: list[str]) -> None:
        self.failures.extend(messages)


# ===========================================================================
# 加载：路由清单 / Controller / 服务配置
# ===========================================================================


def load_generator():
    """按路径加载同目录的生成器（文件名含连字符，不能直接 import）。"""
    script = REPO_ROOT / "data" / "scripts" / "gen-gateway-routes.py"
    if not script.exists():
        raise GateError(f"缺少生成器脚本: {script}")
    spec = importlib.util.spec_from_file_location("gen_gateway_routes", script)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def load_routes() -> list[Route]:
    """读取 routes-nacos.yaml 的路由清单。"""
    manifest = load_generator().read_manifest()
    routes = []
    for index, item in enumerate(manifest):
        uri = item.get("uri", "")
        match = re.match(r"^lb://([a-z0-9-]+)$", uri)
        if not match:
            raise GateError(f"路由 {item.get('id')} 的 uri 非 lb:// 形式: {uri!r}（清单须以服务名表达）")
        patterns: list[str] = []
        for predicate in item.get("predicates", []):
            if predicate.get("name") == "Path":
                raw = predicate.get("args", {}).get("pattern", "")
                patterns.extend(part.strip() for part in raw.split(",") if part.strip())
        strip = any(
            filtr.get("name") == "StripPrefix" for filtr in item.get("filters", [])
        )
        routes.append(
            Route(
                route_id=item.get("id", ""),
                service=match.group(1),
                patterns=patterns,
                strip_prefix=strip,
                order=int(item.get("order", 0)),
                index=index,
            )
        )
    if not routes:
        raise GateError("路由清单为空")
    return routes


def normalize_mapping(raw: str) -> str:
    """把 @RequestMapping 的原始值归一成路径片段列表。

    支持：占位符默认值 ${a.b:/scim/v2}、数组写法里的单个值、无斜杠前缀。

    :param raw: 注解里的字符串字面量
    :return: 小写化的路径段（不含空段）
    """
    value = raw.strip()
    placeholder = PLACEHOLDER_DEFAULT_RE.match(value)
    if placeholder:
        value = placeholder.group(1)
    return [segment for segment in value.strip("/").split("/") if segment]


def _split_attributes(body: str) -> list[str]:
    """按顶层逗号切分注解属性，忽略字符串/花括号/圆括号内部的逗号。

    :param body: 注解圆括号内的文本
    :return: 属性片段列表（每项形如 value = "/a" 或裸位置参数 {"/a", "/b"}）
    """
    items: list[str] = []
    current: list[str] = []
    depth = 0
    in_string = False
    index = 0
    while index < len(body):
        char = body[index]
        if in_string:
            current.append(char)
            if char == "\\":
                current.append(body[index + 1] if index + 1 < len(body) else "")
                index += 2
                continue
            if char == '"':
                in_string = False
            index += 1
            continue
        if char == '"':
            in_string = True
        elif char in "({[":
            depth += 1
        elif char in ")}]":
            depth -= 1
        if char == "," and depth == 0:
            items.append("".join(current))
            current = []
        else:
            current.append(char)
        index += 1
    if current:
        items.append("".join(current))
    return items


def _annotation_paths(text: str, start: int) -> list[str]:
    """取出 Mapping 注解括号内的全部路径字面量。

    两条真实坑：
    - @PostMapping({"/v1/monitor/sourcemaps", "/monitor/sourcemaps"}) 只读第一个字符串，
      会让门禁看不见"还有一个已被路由的规范路径"，把可用接口报成 404。
    - @PostMapping(value = "/cronjob/{id}/trigger", params = "holdLock") 里的 params 值是
      命名字面量而非路径；按"括号内所有字符串"提取会凭空造出 /internal/holdLock 这个端点。

    :param text: （已屏蔽注释的）Java 源码
    :param start: 注解 @ 的起始下标
    :return: 路径字面量列表（无参或仅非路径属性时为空）
    """
    open_index = text.find("(", start)
    if open_index < 0:
        return []
    depth = 0
    close_index = -1
    for index in range(open_index, len(text)):
        char = text[index]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                close_index = index
                break
    if close_index < 0:
        return []
    paths: list[str] = []
    for item in _split_attributes(text[open_index + 1 : close_index]):
        named = NAMED_ATTR_RE.match(item)
        # 只有 value/path（或裸位置参数）承载路径；method/params/headers/produces/consumes 跳过
        if named and named.group(1) not in PATH_ATTR_NAMES:
            continue
        region = named.group(2) if named else item
        paths.extend(QUOTED_RE.findall(region))
    return paths


def mask_comments(text: str) -> tuple[str, bytearray]:
    """屏蔽 Java 注释、标记字面量区间，长度与行号保持与原文一致。

    注释里的 {@code @RequestMapping} 不是真注解：它会让类级前缀取到注释之后的第一个字符串
    字面量（如 @ApiVersion("26.10.01")），把整批接口报成 /api/26.10.01/** 这种不存在的路径。

    :param text: Java 源码原文
    :return: (注释替换为空格的文本, 每个下标是否位于代码区)；字符串字面量内容保留但标为非代码区
    """
    masked = list(text)
    code = bytearray(b"\x01" * len(text))
    index = 0

    def erase(start: int, end: int) -> None:
        """注释：字符抹成空格（保留换行，行号不变）并标为非代码区。"""
        for position in range(start, min(end, len(text))):
            if text[position] != "\n":
                masked[position] = " "
                code[position] = 0

    def mark_literal(start: int, end: int) -> None:
        """字面量：内容原样保留（注解取值要靠它），只标为非代码区。"""
        for position in range(start, min(end, len(text))):
            code[position] = 0

    while index < len(text):
        char = text[index]
        pair = text[index : index + 2]
        triple = text[index : index + 3]
        if pair == "//":
            end = text.find("\n", index)
            end = len(text) if end < 0 else end
            erase(index, end)
            index = end
        elif pair == "/*":
            end = text.find("*/", index + 2)
            end = len(text) if end < 0 else end + 2
            erase(index, end)
            index = end
        elif triple == '"""':
            end = text.find('"""', index + 3)
            end = len(text) if end < 0 else end + 3
            mark_literal(index, end)
            index = end
        elif char in {'"', "'"}:
            quote = char
            end = index + 1
            while end < len(text):
                if text[end] == "\\":
                    end += 2
                    continue
                if text[end] == "\n" and quote == "'":
                    break
                if text[end] == quote:
                    end += 1
                    break
                end += 1
            mark_literal(index, end)
            index = end
        else:
            index += 1
    return "".join(masked), code


def parse_controller(text: str) -> tuple[list[list[str]], list[tuple[list[str], int]]]:
    """解析单个 Controller 源码，得到类级前缀与方法级路径。

    :param text: Java 源码
    :return: (类级路径段列表, [(方法级路径段列表, 行号), ...])；非 Controller 返回 ([], [])
    """
    masked, code = mask_comments(text)
    if not CONTROLLER_MARKER_RE.search(masked):
        return [], []
    class_match = CLASS_DECL_RE.search(masked)
    split_at = class_match.start() if class_match else 0
    class_paths: list[list[str]] = []
    method_mappings: list[tuple[list[str], int]] = []
    for annotation in MAPPING_ANNOTATION_RE.finditer(masked):
        if not code[annotation.start()]:
            continue
        segment_list = [
            segments
            for segments in (
                normalize_mapping(raw) for raw in _annotation_paths(masked, annotation.start())
            )
            if segments
        ]
        if not segment_list:
            continue
        if annotation.start() < split_at:
            if not class_paths:
                class_paths = segment_list
        else:
            line = masked.count("\n", 0, annotation.start()) + 1
            method_mappings.append((segment_list, line))
    return class_paths, method_mappings


def scan_controllers() -> list[Endpoint]:
    """扫描各 -web 模块的 Controller，还原其真实可访问路径。

    :return: 后端路径清单（不含网关 /api 前缀）
    """
    endpoints: list[Endpoint] = []
    for web_dir in sorted(REPO_ROOT.glob("ydsz-*/ydsz-*-web/src/main/java")):
        # 目录形如 <repo>/ydsz-system/ydsz-system-web/src/main/java → service = ydsz-system
        service = web_dir.relative_to(REPO_ROOT).parts[0]
        for java_file in sorted(web_dir.rglob("*.java")):
            text = java_file.read_text(encoding="utf-8", errors="replace")
            class_paths, method_mappings = parse_controller(text)
            if not class_paths and not method_mappings:
                continue
            for class_segments in class_paths or [[]]:
                root = "/".join(class_segments)
                for method_alternatives, line in method_mappings or [([], 0)]:
                    for method_segments in method_alternatives or [[]]:
                        method_root = "/".join(method_segments)
                        joined = "/".join(part for part in (root, method_root) if part)
                        if not joined or joined.startswith("actuator"):
                            continue
                        path = "/" + joined
                        path = re.sub(r"\{[^}]*\}", "sample", path)
                        endpoints.append(
                            Endpoint(
                                service=service,
                                path=path,
                                source=f"{java_file.relative_to(REPO_ROOT)}:{line}",
                            )
                        )
    if not endpoints:
        raise GateError("未扫描到任何 Controller 路径，门禁失去判断依据")
    return endpoints


def service_names() -> dict[str, str]:
    """读取每个 -web 模块声明的 spring.application.name（键为模块目录名 = 路由期望的服务名）。"""
    names: dict[str, str] = {}
    for web_dir in sorted(REPO_ROOT.glob("ydsz-*/ydsz-*-web/src/main/resources")):
        service = web_dir.relative_to(REPO_ROOT).parts[0]
        for candidate in ("application.yml", "bootstrap.yml"):
            path = web_dir / candidate
            if not path.exists():
                continue
            match = re.search(r"^  application:\n(?:.*\n)*?^    name:\s*(\S+)\s*$",
                              path.read_text(encoding="utf-8", errors="replace"), re.MULTILINE)
            if match:
                names[service] = match.group(1)
                break
    return names


def gateway_declared_port() -> int | None:
    """网关自身 bootstrap.yml 声明的监听端口（运行时真值）。"""
    bootstrap = GATEWAY_RES / "bootstrap.yml"
    if not bootstrap.exists():
        raise GateError(f"缺少 {bootstrap}")
    return load_generator()._scalar_port(bootstrap)


# ===========================================================================
# 匹配原语
# ===========================================================================


def pattern_to_regex(pattern: str) -> re.Pattern[str]:
    """把 Spring Ant 风格路径断言转成正则（支持 ** / * / 通配段）。"""
    out: list[str] = []
    index = 0
    length = len(pattern)
    while index < length:
        char = pattern[index]
        if pattern.startswith("**", index):
            # /api/** 既匹配 /api/x 也匹配 /api 本身
            if out and out[-1] == "/":
                out[-1] = "(?:/.*)?"
            else:
                out.append(".*")
            index += 2
            continue
        if char == "*":
            out.append("[^/]*")
        elif char == "?":
            out.append("[^/]")
        elif char == "{":
            end = pattern.find("}", index)
            out.append("[^/]+")
            index = end + 1 if end > 0 else index + 1
            continue
        else:
            out.append(re.escape(char))
        index += 1
    return re.compile("^" + "".join(out) + "$")


def matches(route: Route, path: str) -> bool:
    """路由的任一路径断言是否命中给定请求路径。"""
    return any(pattern_to_regex(pattern).match(path) for pattern in route.patterns)


def pick_route(routes: list[Route], path: str) -> Route | None:
    """模拟 SCG 的匹配顺序：order 小者优先，同 order 按清单出现顺序。"""
    for route in sorted(routes, key=lambda item: (item.order, item.index)):
        if matches(route, path):
            return route
    return None


# ===========================================================================
# 规则
# ===========================================================================


def rule_generated_block(routes: list[Route]) -> list[str]:
    """R1：application.yml 的静态路由块必须由路由清单生成，且逐字一致。"""
    failures: list[str] = []
    generator = load_generator()
    manifest = generator.read_manifest()
    expected = generator.render_block(manifest)
    text = (GATEWAY_RES / "application.yml").read_text(encoding="utf-8")
    begin = text.find(generator.BEGIN_MARKER)
    end = text.find(generator.END_MARKER)
    if begin < 0 or end < 0:
        return ["R1 application.yml 缺少路由生成标记（routes 块可能被手工改写）"]
    current = text[begin : end + len(generator.END_MARKER)]
    if current != expected:
        failures.append(
            "R1 application.yml 的 routes 块已与 routes-nacos.yaml 漂移，"
            "请执行 python3 data/scripts/gen-gateway-routes.py"
        )
    if len(routes) != len(manifest):
        failures.append("R1 路由清单与门禁模型条目数不一致")
    return failures


def rule_endpoints_routed(routes: list[Route], endpoints: list[Endpoint]) -> list[str]:
    """R2：每个 Controller 路径都必须被路由到它自己的服务，且只剥一次 /api。

    两处降级为告警（由 warn_known_issues 输出，不在此判红）：
      - 命中了登记在册的"抢注路由"（KNOWN_ROUTE_SHADOWING）；
      - 路径本身登记为有意不路由（KNOWN_UNROUTED_PATHS）。

    :param routes: 路由清单模型
    :param endpoints: 后端 Controller 路径清单
    :return: 判红消息列表
    """
    failures: list[str] = []
    for endpoint in endpoints:
        request_path = "/api" + endpoint.path
        winner = pick_route(routes, request_path)
        if winner is None:
            if endpoint.path in KNOWN_UNROUTED_PATHS:
                continue
            failures.append(
                f"R2 未路由：{endpoint.service} 的 {request_path} 无匹配路由"
                f"（{endpoint.source}）→ 网关直接 404"
            )
            continue
        if winner.service != endpoint.service:
            if winner.route_id in KNOWN_ROUTE_SHADOWING:
                continue
            failures.append(
                f"R2 错路由：{request_path} 命中 {winner.route_id} → {winner.service}，"
                f"但实现在 {endpoint.service}（{endpoint.source}）"
            )
            continue
        if not winner.strip_prefix:
            failures.append(
                f"R2 缺 StripPrefix：{winner.route_id} 命中 {request_path} 却不剥 /api，"
                f"下游将收到 {request_path} 而非 {endpoint.path}"
            )
    return failures


def warn_known_issues(routes: list[Route], endpoints: list[Endpoint]) -> list[str]:
    """把登记在册的历史包袱显式打出来：可见、可数，但只告警不判红。

    登记项必须同时说明"为什么会存在"和"什么条件下删除"，否则它就是一个永久豁免。

    :param routes: 路由清单模型
    :param endpoints: 后端 Controller 路径清单
    :return: 告警消息列表
    """
    warnings: list[str] = []
    known_route_ids = {route.route_id for route in routes}
    for route_id, reason in KNOWN_ROUTE_SHADOWING.items():
        if route_id not in known_route_ids:
            warnings.append(
                f"豁免过期：{route_id} 已不在路由清单里，请从 KNOWN_ROUTE_SHADOWING 删除"
            )
            continue
        shadowed = [
            endpoint
            for endpoint in endpoints
            if (winner := pick_route(routes, "/api" + endpoint.path)) is not None
            and winner.route_id == route_id
            and endpoint.service != next(r.service for r in routes if r.route_id == route_id)
        ]
        if not shadowed:
            warnings.append(
                f"豁免过期：{route_id} 当前没有被他服务实现的路径，请从 KNOWN_ROUTE_SHADOWING 删除"
            )
            continue
        preview = "、".join(f"{item.service}:{item.path}" for item in shadowed[:3])
        warnings.append(
            f"已知抢注（{len(shadowed)} 个端点不可达）：{route_id} 覆盖 {preview} —— {reason}"
        )
    for path, reason in KNOWN_UNROUTED_PATHS.items():
        matched = [endpoint for endpoint in endpoints if endpoint.path == path]
        if not matched:
            warnings.append(f"豁免过期：后端已无 {path}，请从 KNOWN_UNROUTED_PATHS 删除")
            continue
        if pick_route(routes, "/api" + path) is not None:
            warnings.append(f"豁免过期：{path} 现在已有路由，请从 KNOWN_UNROUTED_PATHS 删除")
            continue
        warnings.append(f"有意不路由：{path} —— {reason}")
    return warnings


def rule_no_orphan_routes(routes: list[Route], endpoints: list[Endpoint]) -> list[str]:
    """R3：路由不能指向根本不存在的实现（死路由会掩盖真实的 404）。"""
    failures: list[str] = []
    by_service: dict[str, list[Endpoint]] = {}
    for endpoint in endpoints:
        by_service.setdefault(endpoint.service, []).append(endpoint)
    for route in routes:
        if route.route_id in ROUTE_ALLOWLIST:
            continue
        candidates = by_service.get(route.service, [])
        hit = False
        for endpoint in candidates:
            browser_path = "/api" + endpoint.path
            if (route.strip_prefix and matches(route, browser_path)) or (
                not route.strip_prefix and matches(route, endpoint.path)
            ):
                hit = True
                break
        if not hit:
            failures.append(
                f"R3 死路由：{route.route_id} → {route.service} 的 "
                f"{route.patterns} 无任何 Controller 命中"
            )
    return failures


def _ends_with_single_star(pattern: str) -> bool:
    """判断 pattern 是否以「单层通配」结尾（/xxx/*）。

    R4 只禁止 /api/x/*：它只匹配一段，命中它的请求属于哪个模块无法静态判定；
    /api/x/** 明确归属 x 前缀，是允许写法。
    """
    return pattern.rstrip("/").endswith("/*")


def rule_strip_prefix(routes: list[Route]) -> list[str]:
    """R4：YDIZ-API-001 —— /api 命名空间一律剥一次，非 /api 前缀一律不剥。"""
    failures: list[str] = []
    for route in routes:
        for pattern in route.patterns:
            under_api = pattern == "/api" or pattern.startswith("/api/")
            if under_api and not route.strip_prefix:
                failures.append(
                    f"R4 路由 {route.route_id} 的 {pattern} 在 /api 命名空间下却无 StripPrefix"
                )
            if not under_api and route.strip_prefix:
                failures.append(
                    f"R4 路由 {route.route_id} 的 {pattern} 不以 /api 开头，"
                    "StripPrefix 会吃掉业务路径首段"
                )
        if any(pattern.startswith("/api/") and _ends_with_single_star(pattern) for pattern in route.patterns):
            failures.append(
                f"R4 路由 {route.route_id} 在 /api 下使用单层通配 {route.patterns}，"
                "前缀归属无法静态判定，请改为 ** 精确到模块前缀"
            )
    return failures


def rule_controller_no_api(endpoints: list[Endpoint]) -> list[str]:
    """R5：业务 Controller 的 @RequestMapping 不得含 /api 段（网关负责剥离）。"""
    failures: list[str] = []
    for endpoint in endpoints:
        if endpoint.path.startswith("/api/") or endpoint.path == "/api":
            failures.append(
                f"R5 {endpoint.service} 的 Controller 路径含 /api 段：{endpoint.path}"
                f"（{endpoint.source}）违反 YDIZ-API-001"
            )
    return failures


def rule_service_names(routes: list[Route], names: dict[str, str]) -> list[str]:
    """R6：lb://<服务名> 必须与该模块真实注册的 spring.application.name 相同。"""
    failures: list[str] = []
    if not names:
        return ["R6 未读到任何 spring.application.name，无法校验 lb:// 目标"]
    for route in routes:
        declared = names.get(route.service)
        if declared is None:
            failures.append(
                f"R6 路由 {route.route_id} 指向未知服务 {route.service}（无同名模块配置）"
            )
        elif declared != route.service:
            failures.append(
                f"R6 服务名漂移：{route.service} 模块注册名为 {declared}，"
                f"而路由 {route.route_id} 写的是 lb://{route.service} → 发现实例为空，503"
            )
    return failures


def rule_gateway_port(routes_via_lb: dict[str, int]) -> list[str]:
    """R7：网关端口与直连端口各自只允许一个真值。

    :param routes_via_lb: 服务名 → application.yml 直连端口（由生成器解析）
    """
    failures: list[str] = []
    port = gateway_declared_port()
    if port is None:
        return ["R7 无法从 bootstrap.yml 解析网关 server.port"]
    for path in sorted(GATEWAY_RES.glob("config/ydsz-gateway-*.yaml")):
        declared = load_generator()._scalar_port(path)
        if declared is not None and declared != port:
            failures.append(
                f"R7 端口漂移：{path.name} 写 {declared}，网关实际监听 {port}"
            )
    nacos_doc = REPO_ROOT / "docs" / "nacos-config" / "ydsz-gateway.yml"
    if nacos_doc.exists():
        declared = load_generator()._scalar_port(nacos_doc)
        if declared is not None and declared != port:
            failures.append(
                f"R7 端口漂移：{nacos_doc.relative_to(REPO_ROOT)} 写 {declared}，"
                f"网关实际监听 {port}"
            )
    if not routes_via_lb:
        failures.append("R7 未能解析任何服务端口，静态直连路由不可信")
    generator = load_generator()
    for service, port in sorted(routes_via_lb.items()):
        doc = REPO_ROOT / "docs" / "nacos-config" / f"{service}-web.yml"
        declared = generator._scalar_port(doc) if doc.exists() else None
        if declared is not None and declared != port:
            failures.append(
                f"R7 端口漂移：{doc.relative_to(REPO_ROOT)} 写 {declared}，"
                f"而 {service} 模块自身配置为 {port}（Nacos 文档副本会误导排障）"
            )
    return failures


def rule_repository_wired() -> list[str]:
    """R8：声明"Nacos 动态路由是唯一入口"时，必须真的有人构造它。"""
    failures: list[str] = []
    if not AUTOCONFIG_IMPORTS.exists():
        return [f"R8 缺少自动配置注册文件 {AUTOCONFIG_IMPORTS}"]
    registered = [
        line.strip()
        for line in AUTOCONFIG_IMPORTS.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    ]
    sources = {
        path.stem: path
        for path in (REPO_ROOT / "ydsz-gateway" / "src/main/java").rglob("*.java")
    }
    for fqn in registered:
        simple_name = fqn.rsplit(".", 1)[-1]
        source = sources.get(simple_name)
        if source is None:
            failures.append(f"R8 自动配置注册了不存在的类 {fqn}")
            continue
        text = source.read_text(encoding="utf-8", errors="replace")
        if "@AutoConfiguration" not in text and "@Configuration" not in text:
            failures.append(
                f"R8 {fqn} 被注册为自动配置，但类上没有任何配置注解 → 整条链路是死代码"
            )
    repository = sources.get("NacosRouteDefinitionRepository")
    if repository is None:
        return failures
    constructing = [
        path.name
        for path in sources.values()
        if "new NacosRouteDefinitionRepository(" in path.read_text(encoding="utf-8", errors="replace")
    ]
    if not constructing:
        failures.append(
            "R8 NacosRouteDefinitionRepository 无人构造 → 动态路由不生效，"
            "routes-nacos.yaml 的 Nacos 入口只是文档"
        )
    return failures


PRUNED_DIRS = {
    "node_modules",
    "dist",
    "build",
    "coverage",
    ".turbo",
    ".vite",
    "playwright-report",
    "test-results",
    ".git",
}
SOURCE_SUFFIXES = {".ts", ".tsx", ".mts", ".cts", ".js", ".mjs", ".cjs", ".vue", ".py"}
# bash/ 与 .github/ 一并纳入：生成器模板与 CI 命令里的 /api/v1 会被复制进每一次生成产物、
# 每一次新会话（本仓 196 处陈旧注释正是 gen-contract.py 的文件头模板批量产出的）。
SOURCE_ROOTS = ("main/src", "apps", "comm", "bash", ".github")


def walk_source_files(root: Path) -> list[Path]:
    """列出前端待检源码文件，并在遍历中剪掉依赖与构建产物目录。

    用 rglob("*") 会先把 node_modules 整棵树物化成 Path 列表（本仓 30 万+ 条目），
    门禁表现为"卡死"；os.walk 允许就地改 dirs，只下探真正的源码目录。

    :param root: 前端仓库根目录
    :return: 命中的源码文件（按路径排序）
    """
    files: list[Path] = []
    for base in SOURCE_ROOTS:
        start = root / base
        if not start.is_dir():
            continue
        for dirpath, dirnames, filenames in os.walk(start):
            dirnames[:] = sorted(d for d in dirnames if d not in PRUNED_DIRS)
            for name in filenames:
                path = Path(dirpath) / name
                if path.suffix in SOURCE_SUFFIXES:
                    files.append(path)
    return sorted(files)


def find_versioned_paths(content: str) -> list[tuple[int, str]]:
    """找出前端源码中所有 /api/v1 出现行，注释也计入。

    注释里的"路径规范: /api/v1/**"会教后来的开发者和 AI 生成网关根本不路由的路径，与真实
    字面量违规同源，所以不做"只查引号内"的弱判定。

    :param content: 文件内容
    :return: [(行号, 去空白后的行文本), ...]
    """
    return [
        (index, line.strip())
        for index, line in enumerate(content.splitlines(), start=1)
        if "/api/v1" in line
    ]


def nginx_gateway_prefixes(text: str) -> list[str]:
    """提取 nginx 中原样透传给网关的 location 前缀（proxy_pass 不带 URI 改写）。

    :param text: nginx 配置文本
    :return: 形如 ["/api", "/ws"] 的前缀列表；已在 nginx 侧改写路径的 location 不计入
    """
    prefixes: list[str] = []
    for match in re.finditer(r"location\s+(/[^\s{]+)\s*\{(.*?)\n    \}", text, re.DOTALL):
        target = re.search(
            r"proxy_pass\s+http://backend_gateway(?P<uri>[^;\s]*)\s*;", match.group(2)
        )
        if target is None or target.group("uri"):
            continue
        prefixes.append(match.group(1).rstrip("/"))
    return prefixes


def prefix_claimed(routes: list[Route], prefix: str) -> bool:
    """路由清单里是否有落在该前缀之下的路径断言（/api 这类命名空间由子路径认领）。

    :param routes: 路由清单模型
    :param prefix: nginx 透传前缀（不含结尾斜杠）
    :return: 该前缀是否有路由归属
    """
    root = prefix + "/"
    return any(
        pattern == prefix or pattern.startswith(root)
        for route in routes
        for pattern in route.patterns
    )


def rule_proxy_prefix_routed(routes: list[Route], prefixes: list[str]) -> list[str]:
    """R10：nginx 透传给网关的每个前缀都要有路由认领，否则请求进网关即 404 且无人报错。"""
    failures: list[str] = []
    for prefix in prefixes:
        if prefix in KNOWN_UNROUTED_PROXY_PREFIXES or prefix_claimed(routes, prefix):
            continue
        failures.append(
            f"R10 nginx 把 {prefix}/** 透传给网关，但路由清单无人认领 → 全量 404"
        )
    return failures


def warn_proxy_prefixes(routes: list[Route], prefixes: list[str]) -> list[str]:
    """登记在册的未认领前缀降级为告警；不再透传或已被路由认领时提示删除豁免。

    :param routes: 路由清单模型
    :param prefixes: nginx 透传前缀
    :return: 告警消息列表
    """
    warnings: list[str] = []
    for prefix, reason in KNOWN_UNROUTED_PROXY_PREFIXES.items():
        if prefix not in prefixes:
            warnings.append(
                f"豁免过期：nginx 已不再透传 {prefix}/**，请从 KNOWN_UNROUTED_PROXY_PREFIXES 删除"
            )
            continue
        if prefix_claimed(routes, prefix):
            warnings.append(f"豁免过期：网关已路由 {prefix}/**，请从 KNOWN_UNROUTED_PROXY_PREFIXES 删除")
            continue
        warnings.append(f"未认领的透传前缀：{prefix}/** —— {reason}")
    return warnings


def rule_frontend_contract(frontend_root: Path | None, gateway_port: int) -> list[str]:
    """R9：前端代理必须原样透传 /api，并与网关端口一致（否则剥两次或连不上）。"""
    if frontend_root is None:
        return []
    failures: list[str] = []
    nginx = frontend_root / "bash" / "deploy" / "nginx.conf"
    if not nginx.exists():
        raise GateError(f"R9 指定了前端根目录但缺少 {nginx}")
    text = nginx.read_text(encoding="utf-8", errors="replace")
    upstream_ports = re.findall(r"server\s+\S+:(\d+);", text)
    if upstream_ports and str(gateway_port) not in upstream_ports:
        failures.append(
            f"R9 nginx upstream 指向 {sorted(set(upstream_ports))}，网关监听 {gateway_port}"
        )
    location = re.search(r"location\s+/api/\s*\{(.*?)\n    \}", text, re.DOTALL)
    if location is None:
        failures.append("R9 nginx 未找到 location /api/ 块")
    elif "rewrite" in location.group(1):
        failures.append(
            "R9 nginx 的 location /api/ 里有 rewrite：网关还会 StripPrefix=1，"
            "等于剥两次 /api → 全量 404（应原样 proxy_pass）"
        )
    for vite in sorted(list(frontend_root.glob("main/vite.config.mts")) + list(frontend_root.glob("apps/*/vite.config.mts"))):
        content = vite.read_text(encoding="utf-8", errors="replace")
        block = re.search(r"'(?:/|\")?/api'?\s*:\s*\{(.*?)\},?\n", content, re.DOTALL)
        if block is None:
            failures.append(
                f"R9 {vite.relative_to(frontend_root)} 没有 '/api' 代理块："
                "dev 下前端请求无处转发（本仓 10 个 vite 配置历史上都配了，缺失即回归）"
            )
            continue
        body = block.group(1)
        if "rewrite" in body:
            failures.append(
                f"R9 {vite.relative_to(frontend_root)} 的 /api 代理带 rewrite："
                "剥掉 /api 后网关的 Path 断言不再命中，且会二次剥离"
            )
        target = re.search(r"target:\s*'http://[^']:(\d+)'", body)
        if target and target.group(1) != str(gateway_port):
            failures.append(
                f"R9 {vite.relative_to(frontend_root)} 代理端口 {target.group(1)} ≠ 网关 {gateway_port}"
            )
    for source in walk_source_files(frontend_root):
        content = source.read_text(encoding="utf-8", errors="replace")
        for line_no, line in find_versioned_paths(content):
            failures.append(
                f"R9 {source.relative_to(frontend_root)}:{line_no} 仍出现 /api/v1 路径"
                f"（{line[:60]}）：版本由 X-Api-Version 请求头协商（P0-A2），"
                "网关不路由 /api/v1/**"
            )
    return failures


# ===========================================================================
# 自检：每条规则都要能被反例打红
# ===========================================================================


def self_test() -> int:
    """用合成夹具验证每条规则真的会失败（门禁本身可被证伪）。"""
    checks: list[tuple[str, bool]] = []
    strip_filters = [{"name": "StripPrefix", "args": {"parts": "1"}}]

    def build(route_id, service, pattern, order=0, filters=None, index=0):
        return Route(
            route_id=route_id,
            service=service,
            patterns=[pattern],
            strip_prefix=bool(filters),
            order=order,
            index=index,
        )

    search_route = build("ydsz-system", "ydsz-system", "/api/search/**", -1, strip_filters)
    checks.append(("Ant ** 命中多级路径", matches(search_route, "/api/search/a/b")))
    checks.append(("Ant ** 不吞并不同前缀", not matches(search_route, "/api/dict/a")))
    checks.append(
        ("单层 * 不跨段", not matches(build("x", "y", "/api/a/*", 0, strip_filters), "/api/a/b/c"))
    )
    checks.append(("非 /api 前缀不受 ** 影响", not matches(search_route, "/search")))

    # /api/internal/** 这类宽前缀会把更具体的 internal 路由抢走 —— 只有 order 能救
    shadowed = [
        build("ydsz-userinfo", "ydsz-userinfo", "/api/internal/**", 0, strip_filters, index=0),
        build("ydsz-workflow-internal", "ydsz-workflow", "/api/internal/engine/**", -3, strip_filters, index=1),
    ]
    drifted = [
        build("ydsz-userinfo", "ydsz-userinfo", "/api/internal/**", 0, strip_filters, index=0),
        build("ydsz-workflow-internal", "ydsz-workflow", "/api/internal/engine/**", 0, strip_filters, index=1),
    ]
    engine_endpoint = [Endpoint("ydsz-workflow", "/internal/engine/x", "W.java:1")]
    checks.append(("更具体路由靠 order 胜出", pick_route(shadowed, "/api/internal/engine/x").service == "ydsz-workflow"))
    checks.append(("同 order 时清单顺序决定归属（漂移隐患）", pick_route(drifted, "/api/internal/engine/x").service == "ydsz-userinfo"))
    checks.append(("R2 能判红：宽前缀抢走 internal 路由", bool(rule_endpoints_routed(drifted, engine_endpoint))))
    checks.append(("R2 不误报：order 修正后通过", not rule_endpoints_routed(shadowed, engine_endpoint)))

    search_endpoint = [Endpoint("ydsz-system", "/search", "S.java:1")]
    checks.append(("R2 能判红：前缀完全没有路由", bool(rule_endpoints_routed([build("ydsz-system", "ydsz-system", "/api/dict/**", 0, strip_filters)], search_endpoint))))
    checks.append(("R2 能判红：/api 命中但没剥前缀", bool(rule_endpoints_routed([build("ydsz-system", "ydsz-system", "/api/search/**", 0)], search_endpoint))))

    no_strip = [build("ydsz-system", "ydsz-system", "/api/search/**", 0)]
    checks.append(("R4 能判红：/api 路由缺 StripPrefix", bool(rule_strip_prefix(no_strip))))
    checks.append(("R4 能判红：非 /api 路由却剥前缀", bool(rule_strip_prefix([build("ydsz-userinfo-direct", "ydsz-userinfo", "/saml/**", 0, strip_filters)]))))
    checks.append(("R4 能判红：/api 下单层通配无法判定归属", bool(rule_strip_prefix([build("ydsz-system", "ydsz-system", "/api/monitor/*", 0, strip_filters)]))))
    checks.append(
        (
            "R4 不误报：/** 结尾不是单层通配",
            not rule_strip_prefix([build("ydsz-system", "ydsz-system", "/api/monitor/**", 0, strip_filters)]),
        )
    )
    checks.append(
        ("R4 不误报：/api 带剥 + 外链不剥", not rule_strip_prefix([search_route, build("ydsz-userinfo-direct", "ydsz-userinfo", "/saml/**", -2, index=1)]))
    )

    checks.append(("R5 能判红：Controller 里写了 /api", bool(rule_controller_no_api([Endpoint("ydsz-system", "/api/search", "S.java:1")]))))
    checks.append(("R5 不误报：/apikey 这类业务前缀", not rule_controller_no_api([Endpoint("ydsz-userinfo", "/apikey", "U.java:1")])))

    orphan = [build("ydsz-message", "ydsz-message", "/api/notify/**", 0, strip_filters)]
    real_message = [Endpoint("ydsz-message", "/message/send", "M.java:1")]
    checks.append(("R3 能判红：路由指向空实现", bool(rule_no_orphan_routes(orphan, real_message))))
    checks.append(("R3 不误报：真实路径可命中", not rule_no_orphan_routes([build("ydsz-message", "ydsz-message", "/api/message/**", 0, strip_filters)], real_message)))
    checks.append(("R3 外链路由按后端路径核对", not rule_no_orphan_routes([build("ydsz-userinfo-direct", "ydsz-userinfo", "/saml/**", -2, index=0)], [Endpoint("ydsz-userinfo", "/saml/metadata", "S.java:1")])))
    checks.append(("R3 允许登记在册的非 Controller 路由", not rule_no_orphan_routes([build("ydsz-cronjob-console", "ydsz-cronjob", "/cronjob/*.html", 1, index=0)], [])))

    generator = build("ydsz-generator", "ydsz-generator", "/api/generator/**", 0, strip_filters)
    checks.append(("R6 能判红：lb 名与注册名不一致", bool(rule_service_names([generator], {"ydsz-generator": "ydzs-generator"}))))
    checks.append(("R6 不误报：同名通过", not rule_service_names([generator], {"ydsz-generator": "ydsz-generator"})))
    checks.append(("R6 能判红：指向不存在的模块", bool(rule_service_names([generator], {"ydsz-system": "ydsz-system"}))))

    checks.append(("R9 能判红：nginx 先剥 /api", bool(re.search(r"rewrite", "location /api/ {\n rewrite ^/api/(.*)$ /$1 break;\n }".split("location /api/")[1]))))
    checks.append(("R9 能判红：/api/v1 字面量违规", find_versioned_paths("const u = '/api/v1/search';") == [(1, "const u = '/api/v1/search';")]))
    checks.append(("R9 不误报：相对路径不含 v1", find_versioned_paths("requestClient.get('/search');") == []))
    checks.append(
        (
            "R9 注释里的 /api/v1 同样判红（会误导代码生成）",
            find_versioned_paths(" * <p>路径规范: /api/v1/system/**（kebab-case）")[0][0] == 1,
        )
    )
    checks.append(("R9 模板字符串插值也判红", find_versioned_paths("const p = `/api/v1/${seg}`;") != []))

    # 注解多路径：只读第一个字符串会把"其实有规范路径"的接口误报成 404
    checks.append(
        (
            "注解多路径全部提取",
            _annotation_paths('@PostMapping({"/v1/monitor/sourcemaps", "/monitor/sourcemaps"})', 0)
            == ["/v1/monitor/sourcemaps", "/monitor/sourcemaps"],
        )
    )
    checks.append(
        (
            "注解里的 produces/headers 不当作路径",
            _annotation_paths('@GetMapping(value = "/a", produces = "application/json", headers = "X=Y")', 0) == ["/a"],
        )
    )
    checks.append(("无参注解不产生路径", _annotation_paths("@PostMapping()", 0) == []))
    checks.append(
        (
            "params/headers 的命名字面量不当作路径",
            _annotation_paths(
                '@PostMapping(value = "/cronjob/{id}/trigger", params = "holdLock")', 0
            )
            == ["/cronjob/{id}/trigger"],
        )
    )
    checks.append(
        (
            "路径占位符的默认值保留",
            _annotation_paths('@RequestMapping("${scim.base-path:/scim/v2}")', 0)
            == ["${scim.base-path:/scim/v2}"],
        )
    )

    # 注释里的注解不是注解：javadoc 提到 @RequestMapping 会让类前缀取到下一个字符串字面量
    controller_src = "\n".join(
        [
            "/**",
            " * 路径前缀：{@code @RequestMapping} 类级别映射。",
            " * @since 26.10.01",
            " */",
            '@ApiVersion("26.10.01")',
            "@RestController",
            '@RequestMapping("/workflow/engine")',
            "public class FlowTaskController {",
            '  // @GetMapping("/commented-out")',
            '  private static final String DOC = """',
            '      @PostMapping("/inside-text-block")',
            '      """;',
            '  @GetMapping("/task/{taskId}")',
            "}",
        ]
    )
    parsed_class, parsed_methods = parse_controller(controller_src)
    checks.append(("javadoc/行注释/文本块里的注解不参与解析", parsed_class == [["workflow", "engine"]]))
    checks.append(
        (
            "方法路径只取真实注解",
            [segments for alternatives, _ in parsed_methods for segments in alternatives]
            == [["task", "{taskId}"]],
        )
    )
    checks.append(("方法注解行号定位到源码行", bool(parsed_methods) and parsed_methods[0][1] == 13))
    checks.append(("非 Controller 不产出端点", parse_controller("public class Plain { void run() {} }") == ([], [])))

    shadow_route = build("ydsz-system-secondary-auth", "ydsz-system", "/api/auth/secondary-auth/**", -4, strip_filters)
    userinfo_auth = build("ydsz-userinfo", "ydsz-userinfo", "/api/auth/**", 0, strip_filters, index=1)
    shadowed_endpoint = Endpoint("ydsz-userinfo", "/auth/secondary-auth", "U.java:1")
    checks.append(
        (
            "R2 登记在册的抢注只告警",
            not rule_endpoints_routed([shadow_route, userinfo_auth], [shadowed_endpoint]),
        )
    )
    unregistered = build("ydsz-system-other", "ydsz-system", "/api/auth/secondary-auth/**", -4, strip_filters)
    checks.append(
        ("R2 未登记的抢注判红", bool(rule_endpoints_routed([unregistered, userinfo_auth], [shadowed_endpoint])))
    )
    checks.append(
        (
            "R2 能判红：抢注路由还要求剥前缀",
            bool(
                rule_endpoints_routed(
                    [build("ydsz-system-secondary-auth", "ydsz-system", "/api/auth/secondary-auth/**", -4), userinfo_auth],
                    [Endpoint("ydsz-system", "/auth/secondary-auth", "S.java:1")],
                )
            ),
        )
    )
    checks.append(
        (
            "R2 豁免在册的不路由别名",
            not rule_endpoints_routed([userinfo_auth], [Endpoint("ydsz-system", "/v1/monitor/sourcemaps", "M.java:1")]),
        )
    )
    checks.append(
        ("R2 能判红：未登记的未路由路径", bool(rule_endpoints_routed([userinfo_auth], [Endpoint("ydsz-system", "/v2/nope", "M.java:1")])))
    )
    checks.append(
        (
            "豁免清单会因失效而告警",
            any("豁免过期" in item for item in warn_known_issues([userinfo_auth], [Endpoint("ydsz-system", "/v1/monitor/sourcemaps", "M.java:1")])),
        )
    )

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "main" / "src").mkdir(parents=True)
        (root / "main" / "src" / "a.ts").write_text("1", encoding="utf-8")
        (root / "main" / "src" / "node_modules").mkdir()
        (root / "main" / "src" / "node_modules" / "b.ts").write_text("1", encoding="utf-8")
        (root / "apps" / "x" / "dist").mkdir(parents=True)
        (root / "apps" / "x" / "dist" / "c.ts").write_text("1", encoding="utf-8")
        (root / "apps" / "x" / "src").mkdir()
        (root / "apps" / "x" / "src" / "d.vue").write_text("1", encoding="utf-8")
        walked = {p.name for p in walk_source_files(root)}
        checks.append(("R9 源码遍历剪掉 node_modules/dist", walked == {"a.ts", "d.vue"}))

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "bash" / "deploy").mkdir(parents=True)
        (root / "bash" / "deploy" / "nginx.conf").write_text(
            "\n".join(
                [
                    "upstream backend_gateway {",
                    "    server 127.0.0.1:9000;",
                    "}",
                    "location /api/ {",
                    "      proxy_pass http://backend_gateway;",
                    "    }",
                ]
            ),
            encoding="utf-8",
        )
        (root / "main").mkdir()

        def vite_text(extra: str = "") -> str:
            return (
                "export default defineConfig({\n"
                "  server: {\n"
                "    proxy: {\n"
                "      '/api': {\n"
                "        target: 'http://localhost:9000',\n"
                "        changeOrigin: true,\n"
                f"{extra}"
                "      },\n"
                "    },\n"
                "  },\n"
                "});\n"
            )

        (root / "main" / "vite.config.mts").write_text(vite_text(), encoding="utf-8")
        checks.append(("R9 正向对照：接线正确时 0 条", rule_frontend_contract(root, 9000) == []))
        (root / "main" / "vite.config.mts").write_text(
            vite_text("        rewrite: (p) => p.replace(/^\\/api/, ''),\n"), encoding="utf-8"
        )
        checks.append(
            (
                "R9 能判红：vite /api 代理再剥一次前缀",
                any("rewrite" in item for item in rule_frontend_contract(root, 9000)),
            )
        )
        (root / "main" / "vite.config.mts").write_text(
            "export default defineConfig({ server: { proxy: {} } });\n", encoding="utf-8"
        )
        checks.append(
            (
                "R9 不静默放过：vite 缺 '/api' 代理块即判红",
                any("代理块" in item for item in rule_frontend_contract(root, 9000)),
            )
        )
        (root / "main" / "vite.config.mts").write_text(vite_text(), encoding="utf-8")
        (root / "bash" / "gen-contract.py").write_text(
            'hdr = "路径规范: /api/v1/x/**"\n', encoding="utf-8"
        )
        checks.append(
            (
                "R9 覆盖生成器模板：bash/*.py 里的 /api/v1 判红",
                any(
                    "gen-contract.py" in item for item in rule_frontend_contract(root, 9000)
                ),
            )
        )
        (root / "bash" / "gen-contract.py").write_text("hdr = 'ok'\n", encoding="utf-8")
        checks.append(
            (
                "R9 能判红：nginx upstream 端口与网关监听不符",
                any("网关监听 9100" in item for item in rule_frontend_contract(root, 9100)),
            )
        )

    nginx_fixture = "\n".join(
        [
            "location /api/ {",
            "      proxy_pass http://backend_gateway;",
            "    }",
            "location /ws/ {",
            "      proxy_pass http://backend_gateway;",
            "    }",
            "location /health {",
            "      proxy_pass http://backend_gateway/actuator/health;",
            "    }",
        ]
    )
    proxy_prefixes = nginx_gateway_prefixes(nginx_fixture)
    checks.append(("R10 只统计原样透传给网关的 location", proxy_prefixes == ["/api", "/ws"]))
    api_only = ["/api"]
    checks.append(
        (
            "R10 能判红：透传前缀无人认领",
            bool(
                rule_proxy_prefix_routed(
                    [build("ydsz-userinfo-direct", "ydsz-userinfo", "/saml/**", -2)], api_only
                )
            ),
        )
    )
    checks.append(
        (
            "R10 子路径即算认领（/api 由 /api/**/** 分担）",
            not rule_proxy_prefix_routed([search_route], api_only),
        )
    )
    checks.append(
        (
            "R10 不误报：前缀已被路由认领",
            not rule_proxy_prefix_routed(
                [build("ydsz-system", "ydsz-system", "/api/**", 0, strip_filters)], api_only
            ),
        )
    )
    checks.append(
        (
            "R10 登记在册的前缀只告警",
            not rule_proxy_prefix_routed([search_route], proxy_prefixes),
        )
    )
    checks.append(
        (
            "R10 路由认领后豁免提示过期",
            any(
                "豁免过期" in item
                for item in warn_proxy_prefixes(
                    [build("ydsz-message", "ydsz-message", "/ws/**", 0)], ["/ws"]
                )
            ),
        )
    )
    checks.append(
        (
            "R10 nginx 停止透传后豁免提示过期",
            any("豁免过期" in item for item in warn_proxy_prefixes([search_route], ["/api"])),
        )
    )

    failed = [name for name, passed in checks if not passed]
    for name, passed in checks:
        print(f"  {'✅' if passed else '❌'} {name}")
    print(
        f"\n{'✅' if not failed else '❌'} check-gateway-routes 自检："
        f"{len(checks) - len(failed)}/{len(checks)} 条断言通过"
    )
    return 0 if not failed else 1


# ===========================================================================


def main() -> int:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="网关路由一致性门禁")
    parser.add_argument("--frontend-root", default=None, help="前端仓库根目录（缺省则跳过 R9）")
    parser.add_argument("--self-test", action="store_true", help="运行反例夹具自检")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    findings = Findings()
    try:
        routes = load_routes()
        endpoints = scan_controllers()
        names = service_names()
        generator = load_generator()
        ports = {}
        for route in routes:
            try:
                ports[route.service] = generator.resolve_port(route.service)
            except generator.RouteSourceError as exc:
                findings.fail(f"R7 {exc}")
        gateway_port = gateway_declared_port() or 0
    except (GateError, OSError) as exc:
        print(f"❌ 路由门禁无法建立判断依据：{exc}", file=sys.stderr)
        return 1

    findings.extend(rule_generated_block(routes))
    findings.extend(rule_endpoints_routed(routes, endpoints))
    findings.extend(rule_no_orphan_routes(routes, endpoints))
    findings.extend(rule_strip_prefix(routes))
    findings.extend(rule_controller_no_api(endpoints))
    findings.extend(rule_service_names(routes, names))
    findings.extend(rule_gateway_port(ports))
    findings.extend(rule_repository_wired())
    findings.warnings.extend(warn_known_issues(routes, endpoints))

    frontend_root = None
    if args.frontend_root:
        candidate = (REPO_ROOT / args.frontend_root).resolve()
        if candidate.exists():
            frontend_root = candidate
        else:
            findings.warn(f"R9/R10 跳过：前端仓库目录不存在 {candidate}")
    else:
        findings.warn("R9/R10 跳过：未传 --frontend-root，前端 /api/v1 残留与代理前缀认领未被断言")
    if frontend_root is not None:
        findings.extend(rule_frontend_contract(frontend_root, gateway_port))
        nginx_path = frontend_root / "bash" / "deploy" / "nginx.conf"
        if not nginx_path.is_file():
            findings.fail(f"R10 无法判定：前端 nginx 配置不存在 {nginx_path}")
        else:
            nginx_text = nginx_path.read_text(encoding="utf-8", errors="replace")
            proxy_prefixes = nginx_gateway_prefixes(nginx_text)
            findings.extend(rule_proxy_prefix_routed(routes, proxy_prefixes))
            findings.warnings.extend(warn_proxy_prefixes(routes, proxy_prefixes))

    services = sorted({route.service for route in routes})
    print(
        f"路由清单 {len(routes)} 条 / 后端路径 {len(endpoints)} 个 / 服务 {len(services)} 个"
        f"（网关监听 {gateway_port}）"
    )
    for warning in findings.warnings:
        print(f"  ⚠️  {warning}")
    for failure in findings.failures:
        print(f"  ❌ {failure}")
    if findings.failures:
        print(f"\n❌ 网关路由门禁未通过：{len(findings.failures)} 项", file=sys.stderr)
        return 1
    print(
        "✅ 网关路由门禁通过（R1–R10）"
        if frontend_root is not None
        else "✅ 网关路由门禁通过（R1–R8；R9/R10 未执行，见上方告警）"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
