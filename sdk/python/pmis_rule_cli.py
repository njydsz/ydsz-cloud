#!/usr/bin/env python3
"""
pmis-rule-cli: PMIS 规则引擎命令行工具

用法示例:
    python pmis_rule_cli.py list
    python pmis_rule_cli.py get RULE-001
    python pmis_rule_cli.py evaluate --context '{"amount": 1000}'
    python pmis_rule_cli.py nl2rule "当金额超过 1 万时告警"
    python pmis_rule_cli.py health RULE-001
    python pmis_rule_cli.py recommend RULE-001
    python pmis_rule_cli.py install-pack risk-pack-basic
"""

import argparse
import json
import os
import sys

# 将当前目录加入 path 以便导入 pmis_rule_client
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pmis_rule_client import PmisRuleClient


def get_client(args) -> PmisRuleClient:
    """从命令行参数构建客户端"""
    base_url = args.base_url or os.environ.get("PMIS_BASE_URL", "http://localhost:8080")
    token = args.token or os.environ.get("PMIS_TOKEN", "")
    return PmisRuleClient(base_url=base_url, token=token, timeout=args.timeout)


def cmd_list(args):
    """列出全部规则"""
    client = get_client(args)
    rules = client.list_rules()
    if not rules:
        print("（无规则）")
        return
    print(f"共 {len(rules)} 条规则：")
    for r in rules:
        status = "启用" if r.enabled else "禁用"
        print(f"  [{r.code}] {r.name}  严重度={r.default_severity}  状态={status}  优先级={r.priority}")


def cmd_get(args):
    """查询单条规则"""
    client = get_client(args)
    rule = client.get_rule(args.rule_code)
    if not rule:
        print(f"规则不存在: {args.rule_code}")
        sys.exit(1)
    print(json.dumps(rule.to_dict(), indent=2, ensure_ascii=False))


def cmd_evaluate(args):
    """评估规则"""
    client = get_client(args)
    context = json.loads(args.context)
    results = client.evaluate(context)
    if not results:
        print("（无规则触发）")
        return
    print(f"触发 {len(results)} 条规则：")
    for r in results:
        print(f"  [{r.rule_code}] {r.rule_name}  严重度={r.severity}  标题={r.title}")


def cmd_nl2rule(args):
    """自然语言转规则"""
    client = get_client(args)
    rule = client.nl2rule(args.text)
    if not rule:
        print("LLM 不可用或未返回结果")
        sys.exit(1)
    print(json.dumps(rule.to_dict(), indent=2, ensure_ascii=False))


def cmd_health(args):
    """健康度评分"""
    client = get_client(args)
    score = client.health_score(args.rule_code)
    if not score:
        print("AI 增强未启用或规则不存在")
        sys.exit(1)
    print(f"规则: {score.rule_code} ({score.rule_name})")
    print(f"总分: {score.score}  等级: {score.level}")
    print(f"  命中率分项: {score.hit_rate_score}  (实际 {score.hit_rate:.1%})")
    print(f"  错误率分项: {score.error_rate_score}  (实际 {score.error_rate:.1%})")
    print(f"  复杂度分项: {score.complexity_score}")
    print(f"  覆盖率分项: {score.coverage_score}")
    if score.suggestions:
        print("改进建议：")
        for s in score.suggestions:
            print(f"  - {s}")


def cmd_recommend(args):
    """规则推荐"""
    client = get_client(args)
    recs = client.recommend(args.rule_code)
    if not recs:
        print("（无推荐）")
        return
    print(f"共 {len(recs)} 条推荐：")
    for r in recs:
        print(f"  [{r.suggested_code}] {r.suggested_name}")
        print(f"    类型: {r.type}  分数: {r.score:.2f}")
        print(f"    表达式: {r.suggested_expression}")
        print(f"    理由: {r.rationale}")
        print()


def cmd_install_pack(args):
    """安装规则集"""
    client = get_client(args)
    result = client.install_pack(args.pack_code, args.version)
    print(json.dumps(result, indent=2, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(
        description="pmis-rule-cli: PMIS 规则引擎命令行工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--base-url", "-u", help="后端服务地址（默认从 PMIS_BASE_URL 环境变量读取）")
    parser.add_argument("--token", "-t", help="Bearer Token（默认从 PMIS_TOKEN 环境变量读取）")
    parser.add_argument("--timeout", type=int, default=30, help="请求超时秒数（默认 30）")

    sub = parser.add_subparsers(dest="command", help="子命令")

    # list
    sub.add_parser("list", help="列出全部规则")

    # get
    p_get = sub.add_parser("get", help="查询单条规则")
    p_get.add_argument("rule_code", help="规则编码")

    # evaluate
    p_eval = sub.add_parser("evaluate", help="评估规则")
    p_eval.add_argument("--context", "-c", required=True, help='规则上下文 JSON，如 \'{"amount": 1000}\'')

    # nl2rule
    p_nl = sub.add_parser("nl2rule", help="自然语言转规则")
    p_nl.add_argument("text", help="自然语言描述")

    # health
    p_health = sub.add_parser("health", help="规则健康度评分")
    p_health.add_argument("rule_code", help="规则编码")

    # recommend
    p_rec = sub.add_parser("recommend", help="规则推荐")
    p_rec.add_argument("rule_code", help="规则编码")

    # install-pack
    p_pack = sub.add_parser("install-pack", help="安装规则集")
    p_pack.add_argument("pack_code", help="规则集编码")
    p_pack.add_argument("--version", "-v", default="", help="版本号")

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(0)

    dispatch = {
        "list": cmd_list,
        "get": cmd_get,
        "evaluate": cmd_evaluate,
        "nl2rule": cmd_nl2rule,
        "health": cmd_health,
        "recommend": cmd_recommend,
        "install-pack": cmd_install_pack,
    }
    fn = dispatch.get(args.command)
    if fn:
        fn(args)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
