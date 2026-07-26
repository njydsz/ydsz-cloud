#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Scan ydsz-backend Java files and report comment coverage statistics."""

import os
import re
import collections
import sys

BACKEND = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'ydsz-backend')


def count_lines(filepath):
    """Count total lines, code lines, comment lines for a Java file."""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception:
        return None

    lines = content.split('\n')
    total = len(lines)
    code_lines = 0
    comment_lines = 0
    blank_lines = 0
    in_block_comment = False

    for line in lines:
        stripped = line.strip()
        if not stripped:
            blank_lines += 1
            continue
        if in_block_comment:
            comment_lines += 1
            if '*/' in stripped:
                in_block_comment = False
            continue
        if stripped.startswith('//'):
            comment_lines += 1
        elif stripped.startswith('/*'):
            comment_lines += 1
            if '*/' not in stripped:
                in_block_comment = True
        elif stripped.startswith('*'):
            comment_lines += 1
        else:
            code_lines += 1

    return total, code_lines, comment_lines, blank_lines


def has_javadoc(filepath):
    """Check if a Java file has class-level Javadoc."""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception:
        return False
    # Check for Javadoc before class/interface/enum/annotation declaration
    pattern = r'/\*\*[\s\S]*?\*/\s*(public\s+)?(abstract\s+)?(final\s+)?(class|interface|enum|@interface)\s+'
    return bool(re.search(pattern, content))


def has_method_javadoc(filepath):
    """Check if public methods have Javadoc."""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception:
        return 0, 0

    # Find public method declarations
    method_pattern = r'(public\s+|protected\s+)(?!class|interface|enum|static\s+void\s+main)[\w<>\[\],\s]+\s+\w+\s*\([^)]*\)\s*(?:throws\s+[\w,\s]+)?\s*\{'
    methods = re.findall(method_pattern, content)
    total_methods = len(methods)

    # Find Javadoc blocks
    javadoc_pattern = r'/\*\*[\s\S]*?\*/'
    javadocs = re.findall(javadoc_pattern, content)

    return total_methods, len(javadocs)


def scan_directory():
    """Scan all Java files in ydsz-backend."""
    module_stats = collections.OrderedDict()

    for root, dirs, files in os.walk(BACKEND):
        dirs[:] = [d for d in dirs if d not in ('target', '.git')]
        for f in files:
            if not f.endswith('.java'):
                continue
            filepath = os.path.join(root, f)
            rel = os.path.relpath(filepath, BACKEND)
            parts = rel.replace(os.sep, '/').split('/')
            module = parts[0]
            sub = parts[1] if len(parts) > 1 else module

            counts = count_lines(filepath)
            if counts is None:
                continue
            total, code, comment, blank = counts
            has_doc = has_javadoc(filepath)
            methods, javadocs = has_method_javadoc(filepath)

            if module not in module_stats:
                module_stats[module] = {
                    'files': 0,
                    'total_lines': 0,
                    'code_lines': 0,
                    'comment_lines': 0,
                    'blank_lines': 0,
                    'files_without_javadoc': [],
                    'files_low_comment_ratio': [],
                    'total_methods': 0,
                    'total_javadocs': 0,
                }

            s = module_stats[module]
            s['files'] += 1
            s['total_lines'] += total
            s['code_lines'] += code
            s['comment_lines'] += comment
            s['blank_lines'] += blank
            s['total_methods'] += methods
            s['total_javadocs'] += javadocs

            if not has_doc and code > 5:
                s['files_without_javadoc'].append(rel)

            if code > 20 and comment / max(code, 1) < 0.1:
                s['files_low_comment_ratio'].append((rel, comment, code))

    return module_stats


def main():
    stats = scan_directory()
    print('=' * 80)
    print('ydsz-backend Java 注释覆盖率扫描报告')
    print('=' * 80)

    grand_files = 0
    grand_code = 0
    grand_comment = 0
    grand_methods = 0
    grand_javadocs = 0

    for mod, s in sorted(stats.items()):
        ratio = s['comment_lines'] / max(s['code_lines'], 1) * 100
        method_ratio = s['total_javadocs'] / max(s['total_methods'], 1) * 100
        print(f'\n{"=" * 70}')
        print(f'模块: {mod}')
        print(f'  Java 文件数: {s["files"]}')
        print(f'  总行数: {s["total_lines"]}')
        print(f'  代码行: {s["code_lines"]}')
        print(f'  注释行: {s["comment_lines"]}')
        print(f'  注释/代码比: {ratio:.1f}%')
        print(f'  公共方法数: {s["total_methods"]}')
        print(f'  Javadoc 数: {s["total_javadocs"]}')
        print(f'  方法 Javadoc 覆盖率: {method_ratio:.1f}%')
        print(f'  无类级 Javadoc 文件: {len(s["files_without_javadoc"])}')
        print(f'  低注释率文件(<10%): {len(s["files_low_comment_ratio"])}')

        if s['files_without_javadoc'] and len(s['files_without_javadoc']) <= 5:
            print(f'  无 Javadoc 文件列表:')
            for f in s['files_without_javadoc']:
                print(f'    - {f}')
        elif s['files_without_javadoc']:
            print(f'  无 Javadoc 文件列表 (前5个):')
            for f in s['files_without_javadoc'][:5]:
                print(f'    - {f}')

        grand_files += s['files']
        grand_code += s['code_lines']
        grand_comment += s['comment_lines']
        grand_methods += s['total_methods']
        grand_javadocs += s['total_javadocs']

    print(f'\n{"=" * 80}')
    print('汇总')
    print(f'  总 Java 文件数: {grand_files}')
    print(f'  总代码行: {grand_code}')
    print(f'  总注释行: {grand_comment}')
    print(f'  总注释/代码比: {grand_comment / max(grand_code, 1) * 100:.1f}%')
    print(f'  总公共方法数: {grand_methods}')
    print(f'  总 Javadoc 数: {grand_javadocs}')
    print(f'  总方法 Javadoc 覆盖率: {grand_javadocs / max(grand_methods, 1) * 100:.1f}%')


if __name__ == '__main__':
    main()
