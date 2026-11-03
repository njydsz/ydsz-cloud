#!/usr/bin/env python3
"""OpenAPI 运行时健康检查。

用途：
  校验指定服务的 /v3/api-docs 端点是否可访问且返回合法 OpenAPI 3 文档。
  支持通过 --base-url 指定服务地址，默认检查 localhost 的 9 个服务。
  若服务未启动或端点不可访问，返回非零退出码。

该脚本不负责启动服务；OpenAPI 生成由服务启动后自动暴露。
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SERVICES = (
    ("gateway", 9000),
    ("system", 9001),
    ("userinfo", 9002),
    ("message", 9004),
    ("workflow", 9005),
    ("cronjob", 9006),
    ("literule", 9007),
    ("nextwiki", 9003),
    ("agent", 9008),
)


def fetch_openapi(base_url: str, timeout: int = 10) -> dict:
    url = f"{base_url.rstrip('/')}/v3/api-docs"
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        if response.status != 200:
            raise RuntimeError(f"HTTP {response.status}")
        return json.loads(response.read().decode("utf-8"))


def validate_spec(spec: dict) -> list[str]:
    findings: list[str] = []
    if not spec.get("openapi", "").startswith("3."):
        findings.append("缺少合法的 OpenAPI 3 版本字段")
    paths = spec.get("paths")
    if not isinstance(paths, dict) or not paths:
        findings.append("paths 为空")
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description="OpenAPI 运行时健康检查")
    parser.add_argument("--base-url", help="服务基础地址，如 http://localhost:9001")
    parser.add_argument("--service", choices=[name for name, _ in SERVICES], help="单个服务名")
    parser.add_argument("--timeout", type=int, default=10, help="请求超时时间（秒）")
    args = parser.parse_args()

    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except AttributeError:
            pass

    targets = []
    if args.base_url:
        targets.append(("custom", args.base_url))
    elif args.service:
        port = next(port for name, port in SERVICES if name == args.service)
        targets.append((args.service, f"http://localhost:{port}"))
    else:
        targets = [(name, f"http://localhost:{port}") for name, port in SERVICES]

    failures: list[str] = []
    checked = 0
    for name, base_url in targets:
        try:
            spec = fetch_openapi(base_url, args.timeout)
            issues = validate_spec(spec)
            if issues:
                failures.append(f"{name}: {', '.join(issues)}")
            else:
                checked += 1
        except Exception as exc:
            failures.append(f"{name}: 无法访问 {base_url}/v3/api-docs: {exc}")

    print(f"实测：{checked}/{len(targets)} 个服务 OpenAPI 可访问")
    if failures:
        print("❌ OpenAPI 运行时健康检查失败：", file=sys.stderr)
        for item in failures:
            print(f"   {item}", file=sys.stderr)
        return 1

    print("✅ OpenAPI 运行时健康检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
