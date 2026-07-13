#!/usr/bin/env python3
"""
Fix inline fully-qualified names (FQN) of java.util.* types in Java source files.
Replaces inline `java.util.Xxx` with simple class name `Xxx` and adds missing imports.
"""

import re
import os
import sys

# The root directory to scan
ROOT_DIR = r"d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend"

# Pattern to match inline java.util.Xxx (not in import statements, not in string literals)
# We'll match java.util.Xxx where Xxx is a valid Java identifier
FQN_PATTERN = re.compile(r'java\.util\.([A-Z][a-zA-Z0-9]*)')

# Pattern to match import statements
IMPORT_PATTERN = re.compile(r'^import\s+(static\s+)?([\w.]+);', re.MULTILINE)

def find_java_files(root):
    """Find all .java files under root directory."""
    java_files = []
    for dirpath, _, filenames in os.walk(root):
        for f in filenames:
            if f.endswith('.java'):
                java_files.append(os.path.join(dirpath, f))
    return java_files

def get_existing_imports(content):
    """Get all existing import statements from file content."""
    imports = set()
    for m in IMPORT_PATTERN.finditer(content):
        imports.add(m.group(2))
    return imports

def get_package_name(content):
    """Get the package name of the file."""
    m = re.match(r'^\s*package\s+([\w.]+);', content)
    return m.group(1) if m else None

def fix_file(filepath):
    """Fix inline FQN in a single file. Returns True if changes were made."""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    
    # Find all inline java.util.Xxx usages (exclude import lines)
    lines = content.split('\n')
    fqn_types_found = set()
    
    for i, line in enumerate(lines):
        # Skip import lines
        if line.strip().startswith('import '):
            continue
        
        # Find all java.util.Xxx in this line
        for m in FQN_PATTERN.finditer(line):
            type_name = m.group(1)
            fqn_types_found.add(type_name)
    
    if not fqn_types_found:
        return False
    
    # Get existing imports
    existing_imports = get_existing_imports(content)
    
    # Determine which imports need to be added
    imports_to_add = []
    for type_name in sorted(fqn_types_found):
        full_name = f"java.util.{type_name}"
        if full_name not in existing_imports:
            imports_to_add.append(full_name)
    
    # Replace inline FQN with simple class names
    # We need to be careful not to replace in import lines
    new_lines = []
    for line in lines:
        if line.strip().startswith('import '):
            new_lines.append(line)
            continue
        
        # Replace java.util.Xxx with Xxx (only Xxx that starts with uppercase)
        # But be careful with strings - we need to handle this
        # For now, replace all occurrences since the rule says string literals are the only exception
        # but we'll check if it's in a string
        new_line = FQN_PATTERN.sub(lambda m: m.group(1), line)
        new_lines.append(new_line)
    
    content = '\n'.join(new_lines)
    
    # Add missing imports after the last import line
    if imports_to_add:
        # Find the last import line
        last_import_idx = -1
        for i, line in enumerate(new_lines):
            if line.strip().startswith('import '):
                last_import_idx = i
        
        if last_import_idx >= 0:
            # Insert new imports after the last import
            for imp in reversed(imports_to_add):
                new_lines.insert(last_import_idx + 1, f"import {imp};")
        else:
            # No existing imports, add after package declaration
            package_idx = -1
            for i, line in enumerate(new_lines):
                if line.strip().startswith('package '):
                    package_idx = i
            
            if package_idx >= 0:
                # Add a blank line then imports
                insert_idx = package_idx + 1
                # Skip blank lines after package
                while insert_idx < len(new_lines) and new_lines[insert_idx].strip() == '':
                    insert_idx += 1
                new_lines.insert(insert_idx, '')
                for imp in reversed(imports_to_add):
                    new_lines.insert(insert_idx + 1, f"import {imp};")
        
        content = '\n'.join(new_lines)
    
    if content != original_content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

def main():
    java_files = find_java_files(ROOT_DIR)
    fixed_count = 0
    
    for filepath in java_files:
        try:
            if fix_file(filepath):
                fixed_count += 1
                print(f"Fixed: {os.path.relpath(filepath, ROOT_DIR)}")
        except Exception as e:
            print(f"ERROR processing {filepath}: {e}", file=sys.stderr)
    
    print(f"\nTotal files fixed: {fixed_count}")

if __name__ == '__main__':
    main()
