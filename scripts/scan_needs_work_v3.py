#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Robust scanner v3: accurately detect Java files missing class-level Javadoc.
Properly handles multi-line annotations between Javadoc and class declaration.
"""

import os
import re
import json
import collections

BACKEND = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'ydsz-backend')


def has_class_level_javadoc(content):
    """Check if a Java file has class-level Javadoc.
    
    Strategy: Find the class/interface/enum declaration, then walk backwards
    through annotations (handling multi-line) to see if we reach a Javadoc block.
    """
    lines = content.split('\n')
    
    # Find the class/interface/enum declaration line (first occurrence)
    class_decl_line = -1
    for i, line in enumerate(lines):
        stripped = line.strip()
        if not stripped or stripped.startswith('//') or stripped.startswith('/*') or stripped.startswith('*'):
            continue
        # Match class declaration (with optional modifiers)
        match = re.match(
            r'^((?:public|protected|private|abstract|final|static)\s+)*'
            r'(class|interface|enum|@interface)\s+\w+',
            stripped
        )
        if match:
            class_decl_line = i
            break
    
    if class_decl_line < 0:
        return True  # Can't find class declaration, assume it has Javadoc
    
    # Walk backwards from class declaration, skipping annotations and empty lines
    # Track multi-line annotation state
    i = class_decl_line - 1
    paren_depth = 0
    brace_depth = 0
    
    while i >= 0:
        stripped = lines[i].strip()
        
        if not stripped:
            i -= 1
            continue
        
        # If we're inside a multi-line annotation (paren/brace depth > 0)
        if paren_depth > 0 or brace_depth > 0:
            # Count opening and closing parens/braces in this line
            for ch in stripped:
                if ch == '(':
                    paren_depth += 1
                elif ch == ')':
                    paren_depth -= 1
                elif ch == '{':
                    brace_depth += 1
                elif ch == '}':
                    brace_depth -= 1
            i -= 1
            continue
        
        # Check if this line is an annotation
        if stripped.startswith('@'):
            # Count parens/braces to detect multi-line annotations
            for ch in stripped:
                if ch == '(':
                    paren_depth += 1
                elif ch == ')':
                    paren_depth -= 1
                elif ch == '{':
                    brace_depth += 1
                elif ch == '}':
                    brace_depth -= 1
            i -= 1
            continue
        
        # Check if this line ends a Javadoc block
        if stripped.endswith('*/'):
            # Walk backwards to find the start of this comment block
            for j in range(i, -1, -1):
                jstripped = lines[j].strip()
                if '/**' in jstripped:
                    return True  # Found Javadoc
                if jstripped.startswith('/*') and '/**' not in jstripped:
                    return False  # Regular block comment, not Javadoc
                if j == 0:
                    break
            return False
        
        # Check if this line is a single-line comment
        if stripped.startswith('//'):
            i -= 1
            continue
        
        # This is a code line (e.g., an import statement) - no Javadoc found
        return False
    
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


def get_class_decl_line(content):
    """Get the line number of the class declaration."""
    lines = content.split('\n')
    for i, line in enumerate(lines):
        stripped = line.strip()
        if not stripped or stripped.startswith('//') or stripped.startswith('/*') or stripped.startswith('*'):
            continue
        match = re.match(
            r'^((?:public|protected|private|abstract|final|static)\s+)*'
            r'(class|interface|enum|@interface)\s+\w+',
            stripped
        )
        if match:
            return i
    return -1


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

    # Sort by module, then reason (no_javadoc first), then code_lines desc
    reason_order = {'no_class_javadoc': 0, 'low_comment_ratio': 1}
    results.sort(key=lambda x: (x['module'], reason_order.get(x['reason'], 2), -x['code_lines']))

    # Summary
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

    output_path = os.path.join(os.path.dirname(BACKEND), 'scripts', 'needs_work_v3.json')
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f'\nDetailed list saved to: {output_path}')


if __name__ == '__main__':
    main()
