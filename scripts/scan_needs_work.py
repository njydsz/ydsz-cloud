#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Identify Java files in ydsz-backend that need comment improvement."""

import os
import re
import json
import collections

BACKEND = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'ydsz-backend')


def analyze_file(filepath):
    """Analyze a single Java file for comment quality."""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception:
        return None

    lines = content.split('\n')
    total = len(lines)
    code_lines = 0
    comment_lines = 0
    in_block_comment = False

    for line in lines:
        stripped = line.strip()
        if not stripped:
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

    has_class_javadoc = bool(re.search(
        r'/\*\*[\s\S]*?\*/\s*(?:@\w+\s+)*(?:public\s+|protected\s+)?(?:abstract\s+)?(?:final\s+)?(?:class|interface|enum|@interface)\s+',
        content
    ))

    ratio = comment_lines / max(code_lines, 1) * 100

    return {
        'total_lines': total,
        'code_lines': code_lines,
        'comment_lines': comment_lines,
        'comment_ratio': round(ratio, 1),
        'has_class_javadoc': has_class_javadoc,
        'needs_work': (not has_class_javadoc and code_lines > 5) or (code_lines > 20 and ratio < 15),
        'priority': 'high' if (not has_class_javadoc and code_lines > 10) else
                    ('medium' if (not has_class_javadoc or ratio < 15) else 'low')
    }


def main():
    results = []
    for root, dirs, files in os.walk(BACKEND):
        dirs[:] = [d for d in dirs if d not in ('target', '.git')]
        for f in files:
            if not f.endswith('.java'):
                continue
            filepath = os.path.join(root, f)
            rel = os.path.relpath(filepath, BACKEND).replace(os.sep, '/')
            info = analyze_file(filepath)
            if info and info['needs_work']:
                info['file'] = rel
                parts = rel.split('/')
                info['module'] = parts[0]
                info['submodule'] = parts[1] if len(parts) > 1 else parts[0]
                results.append(info)

    # Sort by module, then priority (high first), then code_lines (desc)
    priority_order = {'high': 0, 'medium': 1, 'low': 2}
    results.sort(key=lambda x: (x['module'], priority_order.get(x['priority'], 3), -x['code_lines']))

    # Group by module
    by_module = collections.OrderedDict()
    for r in results:
        by_module.setdefault(r['module'], []).append(r)

    # Print summary
    print(f'Total files needing work: {len(results)}')
    print()
    for mod, files in sorted(by_module.items()):
        high = sum(1 for f in files if f['priority'] == 'high')
        medium = sum(1 for f in files if f['priority'] == 'medium')
        low = sum(1 for f in files if f['priority'] == 'low')
        print(f'{mod}: {len(files)} files (high={high}, medium={medium}, low={low})')

    # Save detailed list to JSON
    output_path = os.path.join(os.path.dirname(BACKEND), 'scripts', 'needs_work_files.json')
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f'\nDetailed list saved to: {output_path}')


if __name__ == '__main__':
    main()
