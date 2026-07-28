#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
胖 Controller 扫描脚本。

判定指标（满足任意 2 项即视为胖 Controller）：
1. 文件行数 > 300 行
2. 单个方法行数 > 50 行
3. @Autowired / @Resource 注入的依赖超过 5 个
4. Controller 方法内出现明显的业务编排逻辑：
   - for / while 循环
   - if / else if 业务分支判断（排除参数校验）
   - BeanUtils.copyProperties
   - 多 Service 调用链（同一方法内调用 >= 3 个不同 Service 方法）
5. Controller 方法直接操作 Entity/DO（new XxxDO / XxxDO:: 调用）
6. Controller 内部出现 toVO / convert / assemble 等转换方法
"""

import pathlib
import re
import json
from collections import defaultdict

BACKEND_ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend")

# 业务模块列表（排除 ydsz-common*）
BUSINESS_MODULES = [
    "ydsz-workflow", "ydsz-project", "ydsz-userinfo", "ydsz-system",
    "ydsz-message", "ydsz-cronjob", "ydsz-literule", "ydsz-agent", "ydsz-nextwiki"
]


def find_controllers():
    """查找所有业务模块的 Controller 文件"""
    controllers = []
    for module in BUSINESS_MODULES:
        module_path = BACKEND_ROOT / module
        if not module_path.exists():
            continue
        # 只扫描 web 子模块
        web_path = module_path / f"{module}-web" / "src" / "main" / "java"
        if not web_path.exists():
            continue
        for f in web_path.rglob("*Controller.java"):
            if "/target/" in str(f) or "/test/" in str(f):
                continue
            controllers.append(f)
    return controllers


def count_lines(file_path):
    """统计文件总行数"""
    with open(file_path, "r", encoding="utf-8") as f:
        return sum(1 for _ in f)


def count_injections(content):
    """统计注入的依赖数量（@Autowired / @Resource / final 构造器注入）"""
    # @Autowired / @Resource 字段注入
    field_inject = len(re.findall(r"@(?:Autowired|Resource)\s*(?:\([^)]*\))?\s+(?:private|protected|public)?\s*\w+\s+\w+\s*;", content))
    # @Autowired / @Resource 构造器注入
    ctor_inject = len(re.findall(r"@(?:Autowired|Resource)\s*(?:\([^)]*\))?\s*(?:private|protected|public)?\s+\w+\s*\([^)]*\)", content))
    # Lombok @RequiredArgsConstructor + final 字段
    has_required_args = "@RequiredArgsConstructor" in content
    final_fields = len(re.findall(r"private\s+final\s+\w+\s+\w+\s*;", content))
    if has_required_args:
        return final_fields + field_inject + ctor_inject
    return field_inject + ctor_inject


def extract_methods(content):
    """提取所有方法（返回方法名 + 方法体行数范围 + 方法体内容）"""
    methods = []
    # 匹配方法签名 + 方法体（粗略匹配）
    # 模式：[修饰符] 返回类型 方法名(参数) [throws ...] {
    pattern = re.compile(
        r"(public|protected|private)\s+(?:static\s+)?(?:[\w<>,\s\[\]]+)\s+(\w+)\s*\([^)]*\)\s*(?:throws\s+[\w,\s]+)?\s*\{",
        re.MULTILINE
    )
    for m in pattern.finditer(content):
        method_name = m.group(2)
        # 跳过类构造器（与类名相同）
        start = m.end()
        # 找到匹配的右大括号
        depth = 1
        i = start
        while i < len(content) and depth > 0:
            if content[i] == '{':
                depth += 1
            elif content[i] == '}':
                depth -= 1
            i += 1
        body = content[start:i-1]
        body_lines = body.count('\n')
        # 计算方法所在起始行
        start_line = content[:m.start()].count('\n') + 1
        end_line = content[:i].count('\n') + 1
        methods.append({
            "name": method_name,
            "body_lines": body_lines,
            "start_line": start_line,
            "end_line": end_line,
            "body": body,
        })
    return methods


def detect_business_logic(content, methods):
    """检测业务编排逻辑"""
    violations = []

    # 4a. BeanUtils.copyProperties
    if "BeanUtils.copyProperties" in content:
        violations.append("4a-BeanUtils")

    # 4b. Controller 方法内的 for/while 循环（统计方法体内的循环数）
    method_loops = 0
    for m in methods:
        loops = len(re.findall(r"\bfor\s*\(", m["body"])) + len(re.findall(r"\bwhile\s*\(", m["body"]))
        method_loops += loops
    if method_loops >= 3:
        violations.append(f"4b-loops({method_loops})")

    # 4c. 多 Service 调用链（统计方法体内调用多少个不同的 xxxService. 方法）
    # 简化：统计 content 中调用 service/mapper/repository 的次数
    service_calls = len(re.findall(r"\b\w+(?:Service|Mapper|Repository|Client|Facade)\.\w+\s*\(", content))
    if service_calls >= 15:
        violations.append(f"4c-multiService({service_calls})")

    # 5. Controller 直接操作 Entity/DO
    # new XxxDO() / XxxDO::method
    new_do = len(re.findall(r"\bnew\s+\w+(?:DO|Entity)\s*\(", content))
    do_method_ref = len(re.findall(r"\b\w+(?:DO|Entity)::", content))
    if new_do > 0 or do_method_ref > 0:
        violations.append(f"5-entity({new_do}new,{do_method_ref}ref)")

    # 6. toVO / convert / assemble 方法
    convert_methods = re.findall(r"(?:private|protected|public)\s+(?:static\s+)?[\w<>,\s\[\]]+\s+(to\w*VO|convert\w*|assemble\w*|build\w*VO)\s*\(", content)
    if convert_methods:
        violations.append(f"6-convert({len(convert_methods)})")

    return violations


def analyze_controller(file_path):
    """分析单个 Controller 文件"""
    content = file_path.read_text(encoding="utf-8")
    total_lines = count_lines(file_path)
    injections = count_injections(content)
    methods = extract_methods(content)
    business_violations = detect_business_logic(content, methods)

    # 找出超过 50 行的方法
    long_methods = [m for m in methods if m["body_lines"] > 50]

    # 判定指标命中数
    hits = []
    if total_lines > 300:
        hits.append(f"1-lines({total_lines})")
    if long_methods:
        hits.append(f"2-longMethods({len(long_methods)})")
    if injections > 5:
        hits.append(f"3-injections({injections})")
    hits.extend(business_violations)

    return {
        "file": str(file_path.relative_to(BACKEND_ROOT)),
        "module": file_path.parts[file_path.parts.index("ydsz-backend") + 1],
        "total_lines": total_lines,
        "injections": injections,
        "method_count": len(methods),
        "long_methods": [
            {"name": m["name"], "body_lines": m["body_lines"], "start_line": m["start_line"]}
            for m in long_methods
        ],
        "business_violations": business_violations,
        "hits": hits,
        "is_fat": len(hits) >= 2,
    }


def main():
    controllers = find_controllers()
    print(f"扫描到 {len(controllers)} 个 Controller 文件\n")

    results = []
    for f in controllers:
        try:
            r = analyze_controller(f)
            results.append(r)
        except Exception as e:
            print(f"分析失败: {f} - {e}")

    # 按"胖"程度排序：胖的在前，命中数多的在前
    results.sort(key=lambda x: (-len(x["hits"]), -x["total_lines"]))

    # 输出胖 Controller 列表
    fat_controllers = [r for r in results if r["is_fat"]]
    print("=" * 80)
    print(f"胖 Controller 列表（命中 >= 2 项指标，共 {len(fat_controllers)} 个）")
    print("=" * 80)
    for r in fat_controllers:
        print(f"\n[{r['module']}] {r['file']}")
        print(f"  总行数: {r['total_lines']}  注入数: {r['injections']}  方法数: {r['method_count']}")
        print(f"  命中指标: {', '.join(r['hits'])}")
        if r["long_methods"]:
            print(f"  超长方法:")
            for m in r["long_methods"]:
                print(f"    - {m['name']}() {m['body_lines']} 行 (起于 L{m['start_line']})")

    # 输出全部 Controller 简表（按行数降序）
    print("\n" + "=" * 80)
    print("全部 Controller 简表（按总行数降序）")
    print("=" * 80)
    print(f"{'模块':<20}{'文件':<60}{'行数':<8}{'注入':<6}{'命中':<6}{'胖':<4}")
    print("-" * 110)
    for r in results:
        fat_flag = "✓" if r["is_fat"] else ""
        file_name = r["file"].split("\\")[-1]
        print(f"{r['module']:<20}{file_name:<60}{r['total_lines']:<8}{r['injections']:<6}{len(r['hits']):<6}{fat_flag:<4}")

    # 写入 JSON 便于后续处理
    out_json = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\scripts\fat-controllers-report.json")
    out_json.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n详细报告已写入: {out_json}")

    # 统计
    print("\n" + "=" * 80)
    print("统计")
    print("=" * 80)
    total = len(results)
    fat = len(fat_controllers)
    print(f"总 Controller 数: {total}")
    print(f"胖 Controller 数: {fat} ({fat*100//total}%)")
    print(f"瘦 Controller 数: {total - fat} ({(total-fat)*100//total}%)")

    # 按模块统计
    print("\n按模块统计:")
    module_stats = defaultdict(lambda: {"total": 0, "fat": 0})
    for r in results:
        module_stats[r["module"]]["total"] += 1
        if r["is_fat"]:
            module_stats[r["module"]]["fat"] += 1
    for module, stats in sorted(module_stats.items()):
        print(f"  {module:<20} 总 {stats['total']:<4} 胖 {stats['fat']:<4}")


if __name__ == "__main__":
    main()
