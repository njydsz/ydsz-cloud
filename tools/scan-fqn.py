#!/usr/bin/env python3
"""
扫描 Java 项目中的行内 FQN (Fully Qualified Name) 引用。
根据云顶编码规范 v2.0，禁止在代码体中使用完全限定名。
"""

import os
import re
import json
from pathlib import Path
from collections import defaultdict

PROJECT_ROOT = Path(r"D:\Code\open\ydsz-cloud")

# FQN 正则：匹配在代码体中内联使用的 FQN
FQN_PATTERN = re.compile(
    r'\b((?:java|javax|com\.njydsz|org\.springframework|io\.netty|reactor|'
    r'io\.micrometer|io\.swagger|org\.apache|com\.google|com\.fasterxml|'
    r'org\.slf4j|org\.lombok|cn\.hutool|com\.alibaba|io\.r2dbc|'
    r'org\.assertj|org\.junit|org\.mockito|org\.hamcrest)\.'
    r'[A-Z]\w*(?:\.\w+)*)\b'
)

# import 语句正则
IMPORT_PATTERN = re.compile(r'^\s*import\s+(?:static\s+)?(.+?)\s*;')

# 字符串字面量（粗略排除）
STRING_LITERAL_PATTERN = re.compile(r'"(?:[^"\\]|\\.)*"')


class FQNScanner:
    def __init__(self, project_root: Path):
        self.project_root = project_root
        self.violations = defaultdict(list)
        self.stats = {
            'total_files': 0,
            'scanned_files': 0,
            'files_with_violations': 0,
            'total_violations': 0,
        }
    
    def is_comment_line(self, line: str) -> bool:
        stripped = line.strip()
        return stripped.startswith('//') or stripped.startswith('/*') or stripped.startswith('*')
    
    def extract_imports(self, content: str) -> set:
        imports = set()
        for line in content.split('\n'):
            m = IMPORT_PATTERN.match(line)
            if m:
                full_path = m.group(1)
                imports.add(full_path)
                imports.add(full_path.split('.')[-1])
        return imports
    
    def strip_strings(self, line: str) -> str:
        return STRING_LITERAL_PATTERN.sub('""', line)
    
    def scan_file(self, filepath: Path) -> list:
        violations = []
        try:
            content = filepath.read_text(encoding='utf-8')
        except (UnicodeDecodeError, PermissionError):
            return violations
        
        lines = content.split('\n')
        
        for line_no, line in enumerate(lines, 1):
            stripped = line.strip()
            if not stripped:
                continue
            if IMPORT_PATTERN.match(line):
                continue
            if self.is_comment_line(line):
                continue
            
            line_no_strings = self.strip_strings(line)
            
            for m in FQN_PATTERN.finditer(line_no_strings):
                fqn = m.group(1)
                simple_name = fqn.split('.')[-1]
                violations.append({
                    'line_no': line_no,
                    'line': line.rstrip(),
                    'fqn': fqn,
                    'simple_name': simple_name,
                })
        
        return violations
    
    def scan_project(self):
        java_files = list(self.project_root.rglob("*.java"))
        self.stats['total_files'] = len(java_files)
        
        print(f"开始扫描 {len(java_files)} 个 Java 文件...")
        
        for i, filepath in enumerate(java_files):
            if (i + 1) % 500 == 0:
                print(f"  已扫描 {i + 1}/{len(java_files)} 个文件...")
            
            self.stats['scanned_files'] += 1
            violations = self.scan_file(filepath)
            
            if violations:
                rel_path = str(filepath.relative_to(self.project_root))
                self.violations[rel_path] = violations
                self.stats['files_with_violations'] += 1
                self.stats['total_violations'] += len(violations)
        
        return self
    
    def generate_report(self) -> str:
        lines = []
        lines.append("=" * 80)
        lines.append("行内 FQN 使用扫描报告")
        lines.append("=" * 80)
        lines.append(f"项目路径: {self.project_root}")
        lines.append(f"总文件数: {self.stats['total_files']}")
        lines.append(f"扫描文件数: {self.stats['scanned_files']}")
        lines.append(f"违规文件数: {self.stats['files_with_violations']}")
        lines.append(f"违规总数:  {self.stats['total_violations']}")
        lines.append("")
        
        fqn_stats = defaultdict(int)
        for file_violations in self.violations.values():
            for v in file_violations:
                fqn_stats[v['fqn']] += 1
        
        lines.append("-" * 80)
        lines.append("按 FQN 频率排序 (Top 30):")
        lines.append("-" * 80)
        for fqn, count in sorted(fqn_stats.items(), key=lambda x: -x[1])[:30]:
            lines.append(f"  {count:4d}  {fqn}")
        lines.append("")
        
        lines.append("-" * 80)
        lines.append("详细违规列表:")
        lines.append("-" * 80)
        
        for filepath in sorted(self.violations.keys()):
            violations = self.violations[filepath]
            lines.append(f"\n[{filepath}] ({len(violations)} 处违规)")
            for v in violations:
                lines.append(f"  行 {v['line_no']:4d}: {v['fqn']}")
                lines.append(f"           {v['line'][:100]}")
        
        return '\n'.join(lines)
    
    def export_fix_plan(self) -> dict:
        fix_plan = {}
        for filepath, violations in self.violations.items():
            file_path = self.project_root / filepath
            try:
                content = file_path.read_text(encoding='utf-8')
            except (UnicodeDecodeError, PermissionError):
                continue
            
            needed_imports = set()
            fqn_to_simple = {}
            
            for v in violations:
                fqn = v['fqn']
                simple_name = v['simple_name']
                needed_imports.add(fqn)
                fqn_to_simple[fqn] = simple_name
            
            fix_plan[filepath] = {
                'full_path': str(file_path),
                'violations': violations,
                'imports_to_add': sorted(needed_imports),
                'fqn_to_simple': fqn_to_simple,
            }
        
        return fix_plan


def main():
    scanner = FQNScanner(PROJECT_ROOT)
    scanner.scan_project()
    
    report = scanner.generate_report()
    report_path = PROJECT_ROOT / "tools" / "fqn-violations-report.txt"
    report_path.write_text(report, encoding='utf-8')
    print(f"\n报告已保存至: {report_path}")
    
    fix_plan = scanner.export_fix_plan()
    plan_path = PROJECT_ROOT / "tools" / "fqn-fix-plan.json"
    plan_path.write_text(json.dumps(fix_plan, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f"修复方案已保存至: {plan_path}")
    
    print(f"\n{'='*60}")
    print(f"扫描完成!")
    print(f"违规文件数: {scanner.stats['files_with_violations']}")
    print(f"违规总数:   {scanner.stats['total_violations']}")
    print(f"{'='*60}")


if __name__ == '__main__':
    main()
