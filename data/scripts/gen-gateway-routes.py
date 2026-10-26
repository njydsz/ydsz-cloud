#!/usr/bin/env python3
"""网关静态路由生成器 — routes-nacos.yaml（唯一手工维护源） → application.yml 的 routes 块。

为什么需要它：Spring Cloud Gateway 的 CompositeRouteDefinitionLocator 会把
「配置属性路由」和「RouteDefinitionRepository 路由」按 id 合并，同 id 后加载者覆盖先加载者。
两份清单各自手工维护时，前缀归属就会漂移（历史上 /api/permission/**、/api/internal/** 都漂走过），
表现为生产 404 或请求打到错误服务。因此本仓只保留 ydsz-gateway/src/main/resources/routes-nacos.yaml
一份路由定义，application.yml 里的本地静态路由由本脚本生成：

  - lb://<service> 改写为 http://localhost:<port>，端口取自各服务自身配置（fail-closed：
    解析不到端口直接报错，绝不写死端口表）
  - 无 Nacos 的本地/单机环境因此与生产 Nacos 路由逐条等价

用法：
  python3 data/scripts/gen-gateway-routes.py           # 写入 application.yml
  python3 data/scripts/gen-gateway-routes.py --check   # 只校验（CI 用），漂移即退出 1
  python3 data/scripts/gen-gateway-routes.py --print   # 输出到 stdout
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
MANIFEST = REPO_ROOT / "ydsz-gateway" / "src" / "main" / "resources" / "routes-nacos.yaml"
APPLICATION_YML = REPO_ROOT / "ydsz-gateway" / "src" / "main" / "resources" / "application.yml"

BEGIN_MARKER = "          # BEGIN GENERATED ROUTES —— 由 data/scripts/gen-gateway-routes.py 生成，禁止手工编辑"
END_MARKER = "          # END GENERATED ROUTES"

# 服务端口来源：只认 <service>/<service>-web 的模块配置（运行时真值）与
# docs/nacos-config/<service>-web.yml（下发副本）。不扫 -app：CLI/移动端入口与
# 网关路由目标无关，其 server.port（如随机端口 0）混进来会制造假冲突。
SERVICE_PORT_GLOBS = (
    "{service}/{service}-web/src/main/resources/application*.yml",
    "{service}/{service}-web/src/main/resources/application*.yaml",
    "{service}/{service}-web/src/main/resources/bootstrap.yml",
    "docs/nacos-config/{service}-web.yml",
)

LB_URI_RE = re.compile(r"^lb://([a-z0-9][a-z0-9-]*)$")


class RouteSourceError(RuntimeError):
    """路由清单不可用（缺失/不可解析/端口无法解析）。"""


def read_manifest() -> list[dict]:
    """读取 routes-nacos.yaml 中的 JSON 数组本体。

    该文件头部是大段 YAML 注释，路由本体是一段 JSON 数组 —— 与 Nacos 中
    gateway-routes.json 的内容完全一致，可直接上传。

    :return: RouteDefinition 字典列表
    :raises RouteSourceError: 文件缺失、无 JSON 数组或解析失败
    """
    if not MANIFEST.exists():
        raise RouteSourceError(f"路由清单不存在: {MANIFEST}")
    text = MANIFEST.read_text(encoding="utf-8")
    start = text.find("[")
    end = text.rfind("]")
    if start < 0 or end <= start:
        raise RouteSourceError(f"{MANIFEST.name} 中未找到 JSON 数组路由本体")
    try:
        routes = json.loads(text[start : end + 1])
    except json.JSONDecodeError as exc:
        raise RouteSourceError(f"{MANIFEST.name} JSON 解析失败: {exc}") from exc
    if not isinstance(routes, list) or not routes:
        raise RouteSourceError(f"{MANIFEST.name} 路由数组为空")
    return routes


def _scalar_port(path: Path) -> int | None:
    """从配置文件中取 server.port（仅识别顶层 server: 下的两空格缩进 port）。"""
    if not path.exists():
        return None
    lines = path.read_text(encoding="utf-8").splitlines()
    in_server = False
    for line in lines:
        if line.startswith("server:"):
            in_server = True
            continue
        if in_server:
            if line and not line.startswith((" ", "#")):
                in_server = False
                continue
            match = re.match(r"^  port:\s*(\d+)\s*(?:#.*)?$", line)
            if match:
                return int(match.group(1))
    return None


def service_port_configs(service: str) -> list[Path]:
    """列出某服务端口声明所在的配置文件（去重、稳定顺序）。

    :param service: 服务名（spring.application.name），如 ydsz-userinfo
    :return: 存在的配置文件列表，可能为空
    """
    paths: list[Path] = []
    for template in SERVICE_PORT_GLOBS:
        pattern = template.format(service=service)
        if "*" in pattern:
            paths.extend(sorted(REPO_ROOT.glob(pattern)))
        else:
            candidate = REPO_ROOT / pattern
            if candidate.exists():
                paths.append(candidate)
    unique: dict[str, Path] = {}
    for path in paths:
        unique[str(path)] = path
    return list(unique.values())


def resolve_port(service: str) -> int:
    """解析某个服务的监听端口，用于把 lb:// 改写为本地直连地址。

    候选配置全部读取；若多个文件声明了不同端口则直接失败——按顺序取第一个等于把
    端口漂移藏进"谁先加载"里，而这正是本门禁要消灭的问题。

    :param service: 服务名（spring.application.name），如 ydsz-userinfo
    :return: 端口号
    :raises RouteSourceError: 候选配置都读不到端口，或读到了互相矛盾的端口
    """
    paths = service_port_configs(service)
    found: dict[int, list[str]] = {}
    for path in paths:
        port = _scalar_port(path)
        if port is not None:
            found.setdefault(port, []).append(str(path.relative_to(REPO_ROOT)))
    if not found:
        raise RouteSourceError(
            f"无法解析服务 {service} 的 server.port（已尝试："
            f"{'、'.join(str(p.relative_to(REPO_ROOT)) for p in paths) or '无候选文件'}）。"
            "生成器不内置端口表，端口必须由服务自身配置声明，避免路由清单与真实端口漂移。"
        )
    if len(found) > 1:
        detail = "；".join(
            f"{port} <- {'、'.join(items)}" for port, items in sorted(found.items())
        )
        raise RouteSourceError(f"服务 {service} 的端口声明互相矛盾：{detail}")
    return next(iter(found))


def _path_patterns(route: dict) -> list[str]:
    """提取路由的全部 Path 断言 pattern。"""
    patterns = []
    for predicate in route.get("predicates", []):
        if predicate.get("name") == "Path":
            raw = predicate.get("args", {}).get("pattern", "")
            patterns.extend(part.strip() for part in raw.split(",") if part.strip())
    return patterns


def _filter_shortcut(route: dict) -> list[str]:
    """把 filters 还原为 SCG 的 shortcut 记法（仅支持本清单用到的形式）。"""
    shortcuts = []
    for filtr in route.get("filters", []):
        name = filtr.get("name")
        args = filtr.get("args", {})
        if not args:
            shortcuts.append(name)
        elif name == "StripPrefix" and set(args) == {"parts"}:
            shortcuts.append(f"StripPrefix={args['parts']}")
        else:
            raise RouteSourceError(
                f"生成器不支持的过滤器写法: {name} args={sorted(args)}"
                "（请改用 shortcut 记法或在此处补分支）"
            )
    return shortcuts


def render_block(routes: list[dict]) -> str:
    """渲染 application.yml 的 routes 块（含首尾标记，不含结尾换行）。

    :param routes: 路由清单
    :return: 缩进为 10 空格的 YAML 文本块
    """
    lines = [BEGIN_MARKER, "          routes:"]
    for route in routes:
        route_id = route.get("id")
        uri = route.get("uri", "")
        match = LB_URI_RE.match(uri)
        if match:
            target = f"http://localhost:{resolve_port(match.group(1))}"
        elif uri.startswith("http://") or uri.startswith("https://"):
            target = uri
        else:
            raise RouteSourceError(f"路由 {route_id} 的 uri 非法: {uri!r}")
        patterns = _path_patterns(route)
        if not patterns:
            raise RouteSourceError(f"路由 {route_id} 缺少 Path 断言")
        lines.append(f"            - id: {route_id}")
        lines.append(f"              uri: {target}")
        if "order" in route:
            lines.append(f"              order: {route['order']}")
        lines.append("              predicates:")
        lines.append(f"                - Path={','.join(patterns)}")
        filters = _filter_shortcut(route)
        if filters:
            lines.append("              filters:")
            lines.extend(f"                - {shortcut}" for shortcut in filters)
        metadata = route.get("metadata") or {}
        if metadata:
            lines.append("              metadata:")
            for key, value in metadata.items():
                lines.append(f"                {key}: {value}")
    lines.append(END_MARKER)
    return "\n".join(lines)


def splice(block: str) -> str:
    """把生成的块嵌入 application.yml 的原 routes 区间。"""
    text = APPLICATION_YML.read_text(encoding="utf-8")
    begin = text.find(BEGIN_MARKER)
    if begin < 0:
        raise RouteSourceError(
            f"{APPLICATION_YML.name} 中找不到生成标记；请先确认 routes 块未被手工改写"
        )
    end = text.find(END_MARKER, begin)
    if end < 0:
        raise RouteSourceError(f"{APPLICATION_YML.name} 缺少 {END_MARKER.strip()} 结束标记")
    return text[:begin] + block + text[end + len(END_MARKER) :]


def main() -> int:
    # Windows 控制台默认 GBK，中文判定信息会变乱码；门禁输出必须可读
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="从 routes-nacos.yaml 生成网关静态路由")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="只校验不写盘（CI 用）")
    mode.add_argument("--print", action="store_true", help="输出到 stdout")
    args = parser.parse_args()

    try:
        block = render_block(read_manifest())
    except RouteSourceError as exc:
        print(f"❌ 路由生成失败：{exc}", file=sys.stderr)
        return 1

    if args.print:
        print(block)
        return 0

    current = APPLICATION_YML.read_text(encoding="utf-8")
    updated = splice(block)
    if updated == current:
        print(f"✅ 网关静态路由与 {MANIFEST.name} 一致（{len(read_manifest())} 条）")
        return 0
    if args.check:
        print(
            f"❌ application.yml 的 routes 块已与 {MANIFEST.name} 漂移，"
            "请执行 python3 data/scripts/gen-gateway-routes.py 后提交",
            file=sys.stderr,
        )
        return 1
    APPLICATION_YML.write_text(updated, encoding="utf-8", newline="\n")
    print(f"✅ 已写入 {len(read_manifest())} 条路由到 {APPLICATION_YML.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
