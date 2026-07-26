#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Robust scanner v4: Forward-scanning approach to detect class-level Javadoc.
Finds Javadoc blocks and checks if they precede a class declaration (through annotations).
"""

import os
import re
import json
import collections

BACKEND = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'ydsz-backend')


def has_class_level_javadoc(content):
    """Check if a Java file has class-level Javadoc using forward scanning.
    
    Strategy:
    1. Find all Javadoc blocks (/** ... */)
    2. For each Javadoc block, scan forward through annotations (handling multi-line)
    3. If we reach a class/interface/enum declaration, it has class-level Javadoc
    """
    lines = content.split('\n')
    
    # Find all Javadoc block start/end positions
    javadoc_blocks = []
    i = 0
    while i < len(lines):
        stripped = lines[i].strip()
        if stripped.startswith('/**'):
            start = i
            # Find the end of this Javadoc block
            if '*/' in stripped:
                end = i
            else:
                end = i
                for j in range(i + 1, len(lines)):
                    if '*/' in lines[j]:
                        end = j
                        break
            javadoc_blocks.append((start, end))
            i = end + 1
        else:
            i += 1
    
    if not javadoc_blocks:
        return False
    
    # For each Javadoc block, check if it precedes a class declaration
    for jd_start, jd_end in javadoc_blocks:
        # Scan forward from the end of the Javadoc block
        i = jd_end + 1
        paren_depth = 0
        brace_depth = 0
        in_annotation = False
        
        while i < len(lines):
            stripped = lines[i].strip()
            
            if not stripped:
                i += 1
                continue
            
            # If we're inside a multi-line annotation
            if in_annotation and (paren_depth > 0 or brace_depth > 0):
                for ch in stripped:
                    if ch == '(':
                        paren_depth += 1
                    elif ch == ')':
                        paren_depth -= 1
                    elif ch == '{':
                        brace_depth += 1
                    elif ch == '}':
                        brace_depth -= 1
                if paren_depth <= 0 and brace_depth <= 0:
                    in_annotation = False
                    paren_depth = 0
                    brace_depth = 0
                i += 1
                continue
            
            # Check for annotation
            if stripped.startswith('@'):
                in_annotation = True
                for ch in stripped:
                    if ch == '(':
                        paren_depth += 1
                    elif ch == ')':
                        paren_depth -= 1
                    elif ch == '{':
                        brace_depth += 1
                    elif ch == '}':
                        brace_depth -= 1
                if paren_depth <= 0 and brace_depth <= 0:
                    in_annotation = False
                    paren_depth = 0
                    brace_depth = 0
                i += 1
                continue
            
            # Check for class/interface/enum declaration
            match = re.match(
                r'^((?:public|protected|private|abstract|final|static)\s+)*'
                r'(class|interface|enum|@interface)\s+\w+',
                stripped
            )
            if match:
                return True
            
            # If it's not an annotation, not empty, and not a class declaration,
            # then this Javadoc is not for a class declaration
            # But check if it's a single-line comment or another Javadoc
            if stripped.startswith('//'):
                i += 1
                continue
            
            # It's some other code (like a field or method)
            break
        
        # Also check if the Javadoc block itself contains class-level indicators
        # (sometimes the Javadoc is immediately before the class with no annotations)
    
    return False


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

    reason_order = {'no_class_javadoc': 0, 'low_comment_ratio': 1}
    results.sort(key=lambda x: (x['module'], reason_order.get(x['reason'], 2), -x['code_lines']))

    by_module = collections.OrderedDict()
    for r in results:
        by_module.setdefault(r['module'], []).append(r)

    total_no_javadoc = sum(1 for r in results if r['reason'] == 'no_class_javadoc')
    total_low_ratio = sum(1 for r in results if r['reason'] == 'low_comment_ratio')
    
    print(f'Total files needing work: {len(results)}')
    print(f'  No class Javadoc: {total_no_javadoc}')
    print(f'  Low comment ratio (<15%): {total_low_ratio}')
    print()
    for mod, files in sorted(by_module.items()):
        no_javadoc = sum(1 for f in files if f['reason'] == 'no_class_javadoc')
        low_ratio = sum(1 for f in files if f['reason'] == 'low_comment_ratio')
        print(f'{mod}: {len(files)} files (no_javadoc={no_javadoc}, low_ratio={low_ratio})')

    output_path = os.path.join(os.path.dirname(BACKEND), 'scripts', 'needs_work_v4.json')
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f'\nDetailed list saved to: {output_path}')


if __name__ == '__main__':
    main()
