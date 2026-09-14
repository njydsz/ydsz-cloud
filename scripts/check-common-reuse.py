#!/usr/bin/env python3
"""check-common-reuse.py — 云顶公共模块复用卡点守护脚本（第四次重建，ADR-007/008 拦截承诺落地）。

规则集（与 2026-09-14 深度检查报告一致，重建时沿用原规则语义）：
  E1  同名类            业务模块类名与 ydsz-common 类名同名（需人工核验定性：继承复用/命名冲突/真重复）
  E2  底层 SDK 直连     业务模块绕过 common 封装直连底层 SDK（关键前缀映射）
  E3  HealthIndicator   业务模块 implements HealthIndicator 而未继承 AbstractModuleHealthIndicator
  W1  僵尸依赖          pom 声明 common 模块但模块内零 import（自动装配豁免，仅提示）
  W2  同义后缀          业务自建类与 common 类经后缀规范化后同名
                        （排除 Provider/Sender/Publisher/Collector —— 误报率极高）
  矩阵                  业务模块 × common 模块 import 计数 + 供给侧统计

用法：
  python scripts/check-common-reuse.py            # 全量扫描，输出矩阵 + 命中明细
  python scripts/check-common-reuse.py --quiet    # 仅输出 ERROR 级
  python scripts/check-common-reuse.py --json OUT # 另存 JSON 结果

退出码：存在 E 级命中时为 1（供 CI / pre-commit 拦截），否则 0。
Python 3.13+，零第三方依赖。
"""

from __future__ import annotations

import json
import re
import sys
from collections import defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
COMMON_DIR = REPO_ROOT / "ydsz-common"

# 业务模块清单：根目录下除 ydsz-common / docs / .workbuddy 等外的 ydsz-* 一级目录
EXCLUDED_DIRS = {"ydsz-common", "docs", "sqls", "scripts", "target", ".workbuddy", ".git", ".idea"}

# E2：底层 SDK 直连前缀映射（业务模块禁止 import，应走对应 common 封装）
E2_PREFIXES = {
    "org.apache.rocketmq": "common-queue/common-event（MQ 抽象）",
    "com.baomidou.mybatisplus.core.toolkit.IdWorker": "common-util IdGenerator（ADR-008）",
    "io.jsonwebtoken": "common-auth token 包（P2-5）",
    "org.springframework.data.redis.core.RedisTemplate": "common-redis ops 封装",
    "org.apache.kafka": "common-event（MQ 抽象）",
}

JAVA_FILE_RE = re.compile(r"^import\s+(?:static\s+)?([A-Za-z0-9_.]+)\s*;", re.MULTILINE)
CLASS_DECL_RE = re.compile(r"\b(?:class|interface|enum|record)\s+([A-Z][A-Za-z0-9_]*)")
PACKAGE_RE = re.compile(r"^package\s+([A-Za-z0-9_.]+)\s*;", re.MULTILINE)

# 已定界豁免清单：(规则, 文件路径片段) -> 依据说明。片段与正斜杠化路径匹配。
# 豁免项不计入退出码，但仍打印（DEFERRED）保持可见；定界变化时同步更新本表。
KNOWN_EXCEPTIONS = {
    ("E3", "GatewayHealthIndicator.java"): (
        "网关为 WebFlux 响应式栈，common-web 基类探针为 Servlet 栈阻塞封装，"
        "场景决定必要实现（ADR-011 定界说明）"
    ),
    ("E2", "ydsz-message"): (
        "消息中心为 MQ 重度用户（事务消息/DLQ/批量消费），直连 RocketMQ 有正当性，"
        "ADR-010《MQ 使用边界》定界后豁免"
    ),
}


def discover_biz_modules() -> list[str]:
    """发现业务模块目录（根目录一级 ydsz-*，排除 common 与非代码目录）。"""
    modules = []
    for entry in sorted(REPO_ROOT.iterdir()):
        if entry.is_dir() and entry.name.startswith("ydsz-") and entry.name not in EXCLUDED_DIRS:
            modules.append(entry.name)
    return modules


def collect_common_classes() -> dict[str, str]:
    """收集 common 全部类名 -> 全限定名。"""
    classes: dict[str, str] = {}
    src_root = COMMON_DIR / "*"  # 各子模块
    for java in COMMON_DIR.glob("ydsz-common-*/src/main/java/**/*.java"):
        text = java.read_text(encoding="utf-8", errors="replace")
        pkg_m = PACKAGE_RE.search(text)
        cls_m = CLASS_DECL_RE.search(text)
        if pkg_m and cls_m:
            classes[cls_m.group(1)] = f"{pkg_m.group(1)}.{cls_m.group(1)}"
    del src_root
    return classes


def normalize_suffix(name: str) -> str:
    """W2 后缀规范化：去除常见同义后缀再比较（排除 Provider/Sender/Publisher/Collector）。"""
    for suffix in ("Utils", "Util", "Helper", "Manager", "Support", "Tools"):
        if name.endswith(suffix) and len(name) > len(suffix):
            return name[: -len(suffix)]
    return name


def is_exempt(rule: str, file_str: str) -> str | None:
    """命中已定界豁免清单时返回依据说明，否则返回 None。"""
    for (ex_rule, frag), reason in KNOWN_EXCEPTIONS.items():
        if rule == ex_rule and frag in file_str:
            return reason
    return None


def scan() -> dict:
    """执行全量静态扫描，返回结果字典。"""
    common_classes = collect_common_classes()
    biz_modules = discover_biz_modules()

    errors: list[dict] = []
    warnings: list[dict] = []
    matrix: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    module_dep: dict[str, set[str]] = {}  # 模块 -> pom 声明的 common 模块

    for module in biz_modules:
        imports_by_common: set[str] = set()
        biz_class_names: dict[str, Path] = {}
        pom_text = ""
        pom = module_pom = REPO_ROOT / module / "pom.xml"
        if pom.exists():
            pom_text = pom.read_text(encoding="utf-8", errors="replace")
        del module_pom

        for java in (REPO_ROOT / module).rglob("src/main/java/**/*.java"):
            text = java.read_text(encoding="utf-8", errors="replace")
            rel = java.relative_to(REPO_ROOT)

            # import 收集：矩阵 / E1 / E2 / W1
            for imp in JAVA_FILE_RE.findall(text):
                # E2：底层 SDK 直连（须在 common 过滤前检查，直连的恰恰是非 common import）
                for prefix, suggestion in E2_PREFIXES.items():
                    if imp.startswith(prefix):
                        errors.append(
                            {
                                "rule": "E2",
                                "file": str(rel),
                                "detail": f"直连 {imp}，应使用 {suggestion}",
                            }
                        )

                if not imp.startswith("com.njydsz.common"):
                    continue
                sub = imp.split(".")[3] if imp.count(".") >= 4 else "core"
                if sub == "util" and ".common.util." in imp:
                    sub = "util"
                matrix[module][sub] += 1
                imports_by_common.add(f"ydsz-common-{sub}")

            # E3：HealthIndicator 未继承基类
            for cls_m in CLASS_DECL_RE.finditer(text):
                cls_name = cls_m.group(1)
                biz_class_names.setdefault(cls_name, rel)
                decl_seg = text[cls_m.start() : cls_m.start() + 400]
                if cls_name.endswith("HealthIndicator") and "implements HealthIndicator" in decl_seg:
                    errors.append(
                        {
                            "rule": "E3",
                            "file": str(rel),
                            "detail": f"{cls_name} implements HealthIndicator，应继承 common-web AbstractModuleHealthIndicator",
                        }
                    )
                # ADR-007：禁 BeanUtils.copyProperties
                if "BeanUtils.copyProperties" in text and cls_m is next(iter(CLASS_DECL_RE.finditer(text)), cls_m):
                    errors.append(
                        {
                            "rule": "E2-ADR007",
                            "file": str(rel),
                            "detail": f"{cls_name} 使用 BeanUtils.copyProperties，应改用 common-util BeanMapper（ADR-007）",
                        }
                    )
                    break

            # ADR-008：UUID 主键（UUID.randomUUID 落库场景抽查——所有出现均列出供核验）
            for line_no, line in enumerate(text.splitlines(), 1):
                if "UUID.randomUUID" in line:
                    warnings.append(
                        {
                            "rule": "W-UUID",
                            "file": str(rel),
                            "line": line_no,
                            "detail": f"UUID.randomUUID 使用处（核验是否为 ADR-008 数据库主键违规）：{line.strip()}",
                        }
                    )

        # E1：业务类名与 common 类名同名（同名同义才可疑；纯同名可能是 DDD 语境差异，人工定性）
        for cls_name, rel in biz_class_names.items():
            if cls_name in common_classes and not cls_name.endswith(
                ("Provider", "Sender", "Publisher", "Collector")
            ):
                errors.append(
                    {
                        "rule": "E1",
                        "file": str(rel),
                        "detail": f"业务类 {cls_name} 与 common 同名：{common_classes[cls_name]}（核验：平台内重复 / 命名冲突 / 合法自建）",
                    }
                )

        # W2：同义后缀
        common_norm = {normalize_suffix(n): n for n in common_classes}
        for cls_name, rel in biz_class_names.items():
            norm = normalize_suffix(cls_name)
            if norm in common_norm and norm != cls_name and not cls_name.endswith(
                ("Provider", "Sender", "Publisher", "Collector")
            ):
                warnings.append(
                    {
                        "rule": "W2",
                        "file": str(rel),
                        "detail": f"业务类 {cls_name} 与 common {common_norm[norm]} 同义（后缀规范化后同名）",
                    }
                )

        # W1：僵尸依赖（pom 声明但零 import；自动装配模块豁免——仅提示）
        declared = set(re.findall(r"<artifactId>(ydsz-common-[a-z-]+)</artifactId>", pom_text))
        module_dep[module] = declared
        for dep in declared:
            if dep not in imports_by_common:
                warnings.append(
                    {
                        "rule": "W1",
                        "file": f"{module}/pom.xml",
                        "detail": f"依赖 {dep} 但模块源码零 import（若为自动装配能力则豁免，否则为僵尸依赖）",
                    }
                )

    # 豁免分流：命中 KNOWN_EXCEPTIONS 的 ERROR 转入 deferred（不计退出码，保持可见）
    deferred: list[dict] = []
    kept_errors: list[dict] = []
    for e in errors:
        reason = is_exempt(e["rule"], e["file"])
        if reason:
            deferred.append({**e, "reason": reason})
        else:
            kept_errors.append(e)
    errors = kept_errors

    # 供给侧统计
    supply = {
        sub: {"total": sum(m[sub] for m in matrix.values()), "modules": sum(1 for m in matrix.values() if m[sub] > 0)}
        for sub in {s for m in matrix.values() for s in m}
    }
    supply = dict(sorted(supply.items(), key=lambda kv: -kv[1]["total"]))

    return {
        "errors": errors,
        "deferred": deferred,
        "warnings": warnings,
        "matrix": {m: dict(row) for m, row in sorted(matrix.items())},
        "supply": supply,
        "biz_modules": biz_modules,
    }


def render(result: dict, quiet: bool) -> str:
    """渲染文本报告。"""
    lines = ["=" * 72, "云顶公共模块复用检查（check-common-reuse.py）", "=" * 72]
    lines.append(f"业务模块：{', '.join(result['biz_modules'])}")
    lines.append(f"E 级命中：{len(result['errors'])}    W 级命中：{len(result['warnings'])}")
    lines.append("")

    if result["deferred"]:
        lines.append("-- DEFERRED（已定界豁免，不计退出码）--")
        for d in result["deferred"]:
            lines.append(f"[{d['rule']}] {d['file']}: {d['detail']}")
            lines.append(f"    依据: {d['reason']}")
        lines.append("")

    if result["errors"]:
        lines.append("-- ERROR（CI 拦截项）--")
        for e in result["errors"]:
            lines.append(f"[{e['rule']}] {e['file']}: {e['detail']}")
        lines.append("")
    if result["warnings"] and not quiet:
        lines.append("-- WARN（人工核验项）--")
        for w in result["warnings"]:
            pos = f":{w['line']}" if "line" in w else ""
            lines.append(f"[{w['rule']}] {w['file']}{pos}: {w['detail']}")
        lines.append("")

    lines.append("-- 复用矩阵（业务模块 × common 子模块 import 计数）--")
    for module, row in result["matrix"].items():
        cells = " ".join(f"{k}:{v}" for k, v in sorted(row.items()) if v)
        lines.append(f"{module:<18} {cells}")
    lines.append("")
    lines.append("-- 供给侧（common 子模块：总 import / 覆盖业务模块数）--")
    for sub, stat in result["supply"].items():
        lines.append(f"{sub:<12} total={stat['total']:<5} modules={stat['modules']}")
    return "\n".join(lines)


def main() -> int:
    quiet = "--quiet" in sys.argv
    json_out = None
    if "--json" in sys.argv:
        json_out = sys.argv[sys.argv.index("--json") + 1]

    result = scan()
    print(render(result, quiet))
    if json_out:
        Path(json_out).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return 1 if result["errors"] else 0


if __name__ == "__main__":
    sys.exit(main())
