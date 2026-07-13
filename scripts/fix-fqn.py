#!/usr/bin/env python3
"""Fix inline FQN violations in Java source files."""
import os
import re
import sys

BASE_PACKAGE = "com.njydsz.pmis.common.json"
SRC_DIR = r"d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-json\src"

# Match FQN like com.njydsz.pmis.common.json.subpkg.ClassName
FQN_RE = re.compile(r'(com\.njydsz\.pmis\.common\.json\.(?:\w+\.)+\w+)')

def is_import_or_package(line):
    stripped = line.strip()
    return stripped.startswith('import ') or stripped.startswith('package ')

def is_comment(line):
    stripped = line.strip()
    return stripped.startswith('//') or stripped.startswith('*')

def fix_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    fqns_found = set()
    new_lines = []
    
    for line in lines:
        if is_import_or_package(line) or is_comment(line):
            new_lines.append(line)
            continue
        
        # Find FQNs in this line
        matches = FQN_RE.findall(line)
        if matches:
            for fqn in matches:
                fqns_found.add(fqn)
            # Replace FQNs with simple class names
            line = FQN_RE.sub(lambda m: m.group(1).rsplit('.', 1)[-1], line)
        new_lines.append(line)
    
    if not fqns_found:
        return 0
    
    # Build imports to add
    imports_to_add = []
    content = ''.join(new_lines)
    for fqn in sorted(fqns_found):
        import_line = f"import {fqn};\n"
        if import_line not in content:
            imports_to_add.append(import_line)
    
    if not imports_to_add:
        # Just save the replaced content
        with open(filepath, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)
        return len(fqns_found)
    
    # Find insertion point: after last existing import, or after package line
    insert_idx = 0
    last_import_idx = -1
    package_idx = -1
    for i, line in enumerate(new_lines):
        stripped = line.strip()
        if stripped.startswith('import '):
            last_import_idx = i
        if stripped.startswith('package '):
            package_idx = i
    
    if last_import_idx >= 0:
        insert_idx = last_import_idx + 1
    elif package_idx >= 0:
        insert_idx = package_idx + 2  # blank line after package
    else:
        insert_idx = 0
    
    # Insert imports
    for imp in imports_to_add:
        new_lines.insert(insert_idx, imp)
        insert_idx += 1
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)
    
    return len(fqns_found)

def main():
    total_files = 0
    total_fixes = 0
    
    for root, dirs, files in os.walk(SRC_DIR):
        for fname in files:
            if fname.endswith('.java'):
                filepath = os.path.join(root, fname)
                fixes = fix_file(filepath)
                if fixes > 0:
                    total_files += 1
                    total_fixes += fixes
                    print(f"  Fixed {fixes} FQN(s) in {fname}")
    
    print(f"\nTotal: {total_files} files fixed, {total_fixes} FQN violations resolved")

if __name__ == '__main__':
    main()
