#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Improved scanner: accurately detect Java files missing class-level Javadoc.
Handles multi-line annotations between Javadoc and class declaration.
"""

import os
import re
import json
import collections

BACKEND = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'ydsz-backend')


def has_class_level_javadoc(content):
    """Check if a Java file has class-level Javadoc by examining lines before class declaration."""
    lines = content.split('\n')
    
    # Find the class/interface/enum declaration line
    class_decl_line = -1
    brace_depth = 0
    in_string = False
    in_char = False
    in_block_comment = False
    in_line_comment = False
    
    for i, line in enumerate(lines):
        stripped = line.strip()
        
        # Skip empty lines
        if not stripped:
            continue
            
        # Track comment state
        if in_block_comment:
            if '*/' in stripped:
                in_block_comment = False
            continue
        if stripped.startswith('//'):
            continue
        if stripped.startswith('/*') and not stripped.startswith('/**'):
            if '*/' not in stripped:
                in_block_comment = True
            continue
            
        # Look for class/interface/enum declaration (not inside a method)
        # Simple heuristic: look for keyword at the start of a line (after optional modifiers)
        match = re.match(
            r'^((?:public|protected|private|abstract|final|static)\s+)*'
            r'(class|interface|enum|@interface)\s+\w+',
            stripped
        )
        if match and class_decl_line < 0:
            class_decl_line = i
            break
    
    if class_decl_line < 0:
        return True  # Can't find class declaration, assume it has Javadoc
    
    # Walk backwards from class declaration to find Javadoc or non-annotation/non-empty lines
    found_javadoc = False
    found_annotation = False
    
    for i in range(class_decl_line - 1, -1, -1):
        stripped = lines[i].strip()
        
        if not stripped:
            continue
            
        if stripped.startswith('@'):
            found_annotation = True
            continue
            
        if stripped.endswith('*/'):
            # Found end of a comment block - check if it's a Javadoc
            # Walk backwards to find the start
            for j in range(i, -1, -1):
                if '/**' in lines[j]:
                    found_javadoc = True
                    break
                if lines[j].strip().startswith('/*') and '/**' not in lines[j]:
                    # Regular block comment, not Javadoc
                    break
            break
        
        if stripped.startswith('//'):
            continue
            
        # Found a non-annotation, non-comment, non-empty line
        # This means there's no Javadoc directly before the class (considering annotations)
        break
    
    return found_javadoc


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

    return total, code_lines, comment_lines


def get_class_name(content):
    """Extract class name from Java file."""
    match = re.search(r'\b(?:class|interface|enum)\s+(\w+)', content)
    if match:
        return match.group(1)
    match = re.search(r'@interface\s+(\w+)', content)
    if match:
        return match.group(1)
    return ''


def main():
    results = []
    for root, dirs, files in os.walk(BACKEND):
        dirs[:] = [d for d in dirs if d not in ('target', '.git')]
        for f in files:
            if not f.endswith('.java'):
                continue
            filepath = os.path.join(root, f)
            rel = os.path.relpath(filepath, BACKEND).replace(os.sep, '/')
            
            try:
                with open(filepath, 'r', encoding='utf-8') as fh:
                    content = fh.read()
            except Exception:
                continue
            
            counts = count_lines(filepath)
            if counts is None:
                continue
            total, code, comment = counts
            
            has_javadoc = has_class_level_javadoc(content)
            ratio = comment / max(code, 1) * 100
            
            # A file needs work if:
            # 1. No class-level Javadoc AND code > 5 lines, OR
            # 2. Comment ratio < 15% AND code > 20 lines
            needs_work = (not has_javadoc and code > 5) or (code > 20 and ratio < 15)
            
            if needs_work:
                class_name = get_class_name(content)
                priority = 'high' if (not has_javadoc and code > 10) else 'medium'
                
                results.append({
                    'file': rel,
                    'module': rel.split('/')[0],
                    'submodule': rel.split('/')[1] if len(rel.split('/')) > 1 else '',
                    'class_name': class_name,
                    'total_lines': total,
                    'code_lines': code,
                    'comment_lines': comment,
                    'comment_ratio': round(ratio, 1),
                    'has_class_javadoc': has_javadoc,
                    'needs_work': True,
                    'priority': priority,
                    'reason': 'no_class_javadoc' if not has_javadoc else 'low_comment_ratio',
                })

    # Sort by module, then priority, then code_lines desc
    priority_order = {'high': 0, 'medium': 1}
    results.sort(key=lambda x: (x['module'], priority_order.get(x['priority'], 2), -x['code_lines']))

    # Summary
    by_module = collections.OrderedDict()
    for r in results:
        by_module.setdefault(r['module'], []).append(r)

    print(f'Total files needing work: {len(results)}')
    print()
    for mod, files in sorted(by_module.items()):
        no_javadoc = sum(1 for f in files if f['reason'] == 'no_class_javadoc')
        low_ratio = sum(1 for f in files if f['reason'] == 'low_comment_ratio')
        print(f'{mod}: {len(files)} files (no_javadoc={no_javadoc}, low_ratio={low_ratio})')

    output_path = os.path.join(os.path.dirname(BACKEND), 'scripts', 'needs_work_v2.json')
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f'\nDetailed list saved to: {output_path}')


if __name__ == '__main__':
    main()
