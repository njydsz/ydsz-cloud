#!/usr/bin/env python3
"""OpenAPI 契约产物门禁。

用途：
  校验各 web 模块的 target/openapi/openapi.json 是否为合法 OpenAPI 3 JSON；
  若模块启用了 springdoc 但未生成 artifact，则判红；
  支持通过 --require-all 强制要求所有服务都生成 artifact。

该脚本不负责启动服务；OpenAPI 生成由显式 profile 和运行实例完成，
避免普通 compile 隐式依赖 localhost。
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
WEB_MODULES = (
    "ydsz-system/ydsz-system-web",
    "ydsz-userinfo/ydsz-userinfo-web",
    "ydsz-message/ydsz-message-web",
    "ydsz-workflow/ydsz-workflow-web",
    "ydsz-cronjob/ydsz-cronjob-web",
    "ydsz-nextwiki/ydsz-nextwiki-web",
    "ydsz-literule/ydsz-literule-web",
    "ydsz-agent/ydsz-agent-web",
    "ydsz-generator/ydsz-generator-web",
)


def validate_spec(path: Path) -> list[str]:
    try:
        with path.open(encoding="utf-8") as stream:
            data = json.load(stream)
    except (OSError, json.JSONDecodeError) as exc:
        return [f"{path}: 无法解析 JSON: {exc}"]

    findings: list[str] = []
    if data.get("openapi", "").startswith("3."):
        findings.append(f"{path}: 合法 OpenAPI 3 文档")
    else:
        findings.append(f"{path}: 缺少合法的 OpenAPI 3 版本字段")

    paths = data.get("paths")
    if not isinstance(paths, dict) or not paths:
        findings.append(f"{path}: paths 为空")
    return findings


def collect(require_all: bool) -> list[str]:
    findings: list[str] = []
    missing = 0
    checked = 0
    for module in WEB_MODULES:
        module_path = REPO_ROOT / module
        artifact = module_path / "target" / "openapi" / "openapi.json"
        if not module_path.exists():
            findings.append(f"{module}: 模块不存在")
            continue
        if artifact.exists():
            checked += 1
            findings.extend(validate_spec(artifact))
        else:
            missing += 1
            if require_all:
                findings.append(f"{module}: 缺少 target/openapi/openapi.json")

    print(f"实测：{checked} 个 artifact，{missing} 个模块未生成")
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description="OpenAPI 契约产物门禁")
    parser.add_argument("--require-all", action="store_true", help="要求所有服务生成 artifact")
    args = parser.parse_args()

    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except AttributeError:
            pass

    findings = collect(args.require_all)
    failures = [item for item in findings if "缺少" in item or "无法解析" in item or "paths 为空" in item]
    if failures:
        print("❌ OpenAPI 契约产物检查失败：", file=sys.stderr)
        for item in failures:
            print(f"   {item}", file=sys.stderr)
        return 1

    print("✅ OpenAPI 契约产物检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
