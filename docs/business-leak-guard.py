#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
business-leak-guard.py — 云顶开发框架「业务代码零泄漏」防回归卡点

设计目标：
    公司自研框架（ydsz-* 各模块）只提供**通用、可配置、与业务无关**的能力，
    严禁在框架层落地任何具体业务/领域算法或硬编码业务实体
    （如客户信用评分、授信额度、风控、反欺诈、营销促销、优惠券、积分、
     具体行业利润/定价/合同金额计算、具体客户评级等）。

    本脚本静态扫描所有 ydsz-*/src/main/java 下的 .java 源文件，命中业务关键词即视为
    疑似泄漏，输出 file:line 供人工 triage。作为 CI 卡点或本地 PR 前自查使用。

用法:
    python3 docs/business-leak-guard.py                # 打印命中清单与汇总
    python3 docs/business-leak-guard.py --quiet        # 仅打印汇总计数
    python3 docs/business-leak-guard.py --json out.json
    python3 docs/business-leak-guard.py --root /path   # 指定仓库根（默认脚本上级目录）

说明:
    仅扫描 java 源；排除 target/ 与 .idea/。命中项需人工判断，
    通用机制（如通用评分卡 Scorecard、通用决策表、通用消息/工作流能力）以中性命名，
    不应命中本规则；若误命中，请在调用方收敛命名或扩展 ALLOW 清单。
"""
import os
import re
import sys
import json

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = ("target", ".idea", "node_modules")

# 业务语义关键词：仅保留「具体业务/领域算法或实体」的高精度信号，避免误伤通用框架能力。
# 显式排除（属通用框架能力或设计叙述，非业务泄漏）：
#   - overdue / 超期        → 工作流 SLA 超期（通用概念，非金融逾期）
#   - 准入                  → 接入控制 / 鉴权（通用安全术语）
#   - 风控                  → 文档中"对标竞品风控引擎"的设计叙述，非业务规则
#   - 营销 / 费率 / 报价 / 定价 / 供应链 → 通用通知分类 / 决策表 / 通用商业术语示例
BUSINESS_KEYWORDS = [
    # 中文：具体业务算法 / 业务指标 / 业务实体
    "信用评分", "信用等级", "信用评级", "客户评级", "授信额度", "授信",
    "反欺诈", "欺诈检测", "欺诈", "促销", "优惠券", "积分商城",
    "双费率", "计费利用率", "利润率", "报价", "合同金额", "贷款",
    "供应链", "定价模型", "利润计算", "利润分摊", "盈利", "毛利",
    # 英文精准短语（避免裸 score / risk / overdue 等通用词）
    "creditscore", "credit-score", "creditrating", "dualrate", "dual-rate",
    "anti-fraud", "antifraud", "profitmargin", "profit-margin", "loanapproval",
]

# 被命中的文件若确属误报（通用机制中性命名残留），在此显式放行，并注明原因。
ALLOW = {
    # "path/rel/to/root": "放行原因（@ydsz-team）",
}

RE_BUSINESS = re.compile("|".join(re.escape(k) for k in BUSINESS_KEYWORDS), re.IGNORECASE)


def walk_java():
    files = []
    for dp, dn, fn in os.walk(ROOT):
        parts = dp.split(os.sep)
        if any(s in parts for s in SKIP_DIRS):
            continue
        # 仅扫描框架模块业务源码：ydsz-*/src/main/java
        if not ("src" in parts and "main" in parts and "java" in parts):
            continue
        for f in fn:
            if f.endswith(".java"):
                files.append(os.path.join(dp, f))
    return files


def main():
    args = sys.argv[1:]
    quiet = "--quiet" in args
    json_out = None
    for a in args:
        if a.startswith("--json"):
            json_out = a.split("=", 1)[1] if "=" in a else None
    if "--root" in args:
        idx = args.index("--root")
        global ROOT
        ROOT = os.path.abspath(args[idx + 1])

    hits = []  # (rel, line_no, keyword, snippet)
    for path in walk_java():
        rel = os.path.relpath(path, ROOT)
        if rel in ALLOW:
            continue
        try:
            with open(path, "r", encoding="utf-8", errors="replace") as fh:
                lines = fh.readlines()
        except Exception:
            continue
        for i, line in enumerate(lines, 1):
            m = RE_BUSINESS.search(line)
            if m:
                hits.append((rel, i, m.group(0), line.strip()[:120]))

    by_file = {}
    for rel, ln, kw, snip in hits:
        by_file.setdefault(rel, []).append((ln, kw, snip))

    if not quiet:
        if hits:
            print("⚠️  疑似业务代码泄漏命中：%d 处（%d 个文件）" % (len(hits), len(by_file)))
            print("-" * 80)
            for rel in sorted(by_file):
                print("📄 %s" % rel)
                for ln, kw, snip in by_file[rel]:
                    print("   L%-4d [%-10s] %s" % (ln, kw, snip))
                print("-" * 80)
        else:
            print("✅ 未发现业务代码关键词泄漏（ydsz-*/src/main/java）。")

    print("汇总: 命中 %d 处 / %d 文件" % (len(hits), len(by_file)))

    if json_out:
        with open(json_out, "w", encoding="utf-8") as fh:
            json.dump({"total": len(hits), "files": len(by_file),
                       "hits": [{"file": r, "line": l, "keyword": k, "snippet": s}
                                for r, l, k, s in hits]}, fh, ensure_ascii=False, indent=2)
        print("已写入 %s" % json_out)

    # 命中即视为不合规，供 CI 判定（非零退出）
    sys.exit(1 if hits else 0)


if __name__ == "__main__":
    main()
