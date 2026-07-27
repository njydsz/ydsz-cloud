#!/usr/bin/env python3
"""审计 Java 文件注释质量"""

import subprocess
import os
import re
import sys
from collections import defaultdict

def audit_files(root_dir, sample_size=None):
    result = subprocess.run(['git', 'ls-files', '*.java'], cwd=root_dir, capture_output=True, text=True, encoding='utf-8')
    files = [f for f in result.stdout.strip().split('\n') if f.endswith('.java')]
    
    if sample_size:
        files = files[:sample_size]
    
    stats = {
        'total_files': 0,
        'no_class_javadoc': 0,
        'total_classes': 0,
        'no_field_comments': 0,
        'total_fields': 0,
        'no_method_javadoc': 0,
        'total_public_methods': 0,
        'no_enum_const_comments': 0,
        'total_enum_consts': 0,
    }
    
    module_stats = defaultdict(lambda: dict(stats))
    
    for fpath in files:
        full = os.path.join(root_dir, fpath)
        try:
            with open(full, 'r', encoding='utf-8') as f:
                content = f.read()
        except:
            continue
        
        stats['total_files'] += 1
        module = fpath.split('/')[2] if len(fpath.split('/')) > 2 else 'unknown'
        module_stats[module]['total_files'] += 1
        
        # Check class-level javadoc
        for m in re.finditer(r'(?:public\s+|protected\s+|private\s+|abstract\s+|final\s+|static\s+)*(class|interface|enum)\s+(\w+)', content):
            stats['total_classes'] += 1
            module_stats[module]['total_classes'] += 1
            before = content[:m.start()].rstrip()
            if not before.endswith('*/'):
                stats['no_class_javadoc'] += 1
                module_stats[module]['no_class_javadoc'] += 1
        
        # Count fields without comments
        for m in re.finditer(r'(?:private|protected)\s+(?:final\s+)?(?:static\s+)?\w+(?:<[^>]+>)?\s+(\w+)\s*[;=]', content):
            stats['total_fields'] += 1
            module_stats[module]['total_fields'] += 1
            before = content[:m.start()].rstrip()
            if not before.endswith('*/'):
                stats['no_field_comments'] += 1
                module_stats[module]['no_field_comments'] += 1
        
        # Count public methods without javadoc (exclude getters/setters/toString/equals/hashCode)
        for m in re.finditer(r'public\s+(?:static\s+)?(?:final\s+)?(?:synchronized\s+)?\w+(?:<[^>]+>)?\s+(\w+)\s*\(', content):
            method_name = m.group(1)
            if method_name in ('get', 'set', 'is', 'toString', 'equals', 'hashCode', 'values', 'valueOf'):
                continue
            stats['total_public_methods'] += 1
            module_stats[module]['total_public_methods'] += 1
            before = content[:m.start()].rstrip()
            if not before.endswith('*/'):
                stats['no_method_javadoc'] += 1
                module_stats[module]['no_method_javadoc'] += 1
        
        # Count enum constants without comments
        for m in re.finditer(r'\benum\s+\w+', content):
            # Find enum body
            enum_start = m.start()
            try:
                brace_start = content.index('{', enum_start)
            except ValueError:
                continue
            depth = 1
            pos = brace_start + 1
            while depth > 0 and pos < len(content):
                if content[pos] == '{':
                    depth += 1
                elif content[pos] == '}':
                    depth -= 1
                pos += 1
            enum_body = content[brace_start+1:pos-1]
            
            for em in re.finditer(r'(\w+)\s*[,\(;]', enum_body):
                cname = em.group(1)
                if cname in ('if', 'for', 'while', 'switch', 'return', 'new'):
                    continue
                stats['total_enum_consts'] += 1
                module_stats[module]['total_enum_consts'] += 1
                before = enum_body[:em.start()].rstrip()
                if not before.endswith('*/'):
                    stats['no_enum_const_comments'] += 1
                    module_stats[module]['no_enum_const_comments'] += 1
    
    return stats, module_stats

if __name__ == '__main__':
    root = r'd:\Code\ydsz\ydsz-pmis'
    stats, module_stats = audit_files(root)
    
    print("=" * 60)
    print("Java 注释质量审计报告")
    print("=" * 60)
    print(f"审计文件数: {stats['total_files']}")
    print(f"类缺少 Javadoc: {stats['no_class_javadoc']}/{stats['total_classes']}")
    print(f"字段缺少注释: {stats['no_field_comments']}/{stats['total_fields']}")
    print(f"public 方法缺少 Javadoc: {stats['no_method_javadoc']}/{stats['total_public_methods']}")
    print(f"枚举常量缺少注释: {stats['no_enum_const_comments']}/{stats['total_enum_consts']}")
    print()
    print("按模块排序（按缺少注释的字段比例降序）:")
    print("-" * 80)
    rows = []
    for mod, ms in module_stats.items():
        if ms['total_fields'] > 0:
            field_ratio = ms['no_field_comments'] / ms['total_fields']
        else:
            field_ratio = 0
        rows.append((mod, ms, field_ratio))
    rows.sort(key=lambda x: -x[2])
    for mod, ms, ratio in rows[:20]:
        print(f"  {mod:40s} files={ms['total_files']:4d}  fields_missing={ms['no_field_comments']:4d}/{ms['total_fields']:4d}  methods_missing={ms['no_method_javadoc']:4d}/{ms['total_public_methods']:4d}")
