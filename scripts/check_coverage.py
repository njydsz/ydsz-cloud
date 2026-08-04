#!/usr/bin/env python3
# =====================================================================
#  JaCoCo 覆盖率检查脚本
#  --------------------------------------------------------------------
#  解析 JaCoCo XML 报告，验证行覆盖率和分支覆盖率是否达到阈值
#
#  用法:
#    python check_coverage.py <jacoco.xml> --line-threshold 60 --branch-threshold 50
#
#  退出码:
#    0 - 通过
#    1 - 未通过
# =====================================================================

import argparse
import sys
from pathlib import Path

try:
    import defusedxml.ElementTree as ET
except ImportError:
    import xml.etree.ElementTree as ET


def parse_jacoco_report(xml_path: str) -> dict:
    """解析 JaCoCo XML 报告，返回覆盖率统计"""
    tree = ET.parse(xml_path)
    root = tree.getroot()

    counters = {}
    for counter in root.findall('.//counter'):
        counter_type = counter.get('type')
        missed = int(counter.get('missed', 0))
        covered = int(counter.get('covered', 0))
        counters[counter_type] = {
            'missed': missed,
            'covered': covered,
            'total': missed + covered
        }
    return counters


def calculate_coverage(counters: dict, metric: str) -> float:
    """计算指定指标的覆盖率百分比"""
    if metric not in counters:
        return 0.0
    data = counters[metric]
    total = data['total']
    if total == 0:
        return 100.0
    return (data['covered'] / total) * 100


def main():
    parser = argparse.ArgumentParser(description='JaCoCo 覆盖率检查')
    parser.add_argument('xml_path', help='JaCoCo XML 报告路径')
    parser.add_argument('--line-threshold', type=float, default=60.0,
                        help='行覆盖率阈值（百分比）')
    parser.add_argument('--branch-threshold', type=float, default=50.0,
                        help='分支覆盖率阈值（百分比）')
    args = parser.parse_args()

    xml_path = Path(args.xml_path)
    if not xml_path.exists():
        print(f"❌ 覆盖率报告不存在: {xml_path}")
        sys.exit(1)

    counters = parse_jacoco_report(str(xml_path))

    line_coverage = calculate_coverage(counters, 'LINE')
    branch_coverage = calculate_coverage(counters, 'BRANCH')

    print(f"{'='*50}")
    print(f"  JaCoCo 覆盖率报告")
    print(f"{'='*50}")
    print(f"  行覆盖率:   {line_coverage:.2f}% (阈值: {args.line_threshold}%)")
    print(f"  分支覆盖率: {branch_coverage:.2f}% (阈值: {args.branch_threshold}%)")
    print(f"{'='*50}")

    passed = True
    if line_coverage < args.line_threshold:
        print(f"❌ 行覆盖率不达标: {line_coverage:.2f}% < {args.line_threshold}%")
        passed = False

    if branch_coverage < args.branch_threshold:
        print(f"❌ 分支覆盖率不达标: {branch_coverage:.2f}% < {args.branch_threshold}%")
        passed = False

    if passed:
        print("✅ 覆盖率检查通过")
        sys.exit(0)
    else:
        print("❌ 覆盖率检查未通过")
        sys.exit(1)


if __name__ == '__main__':
    main()
