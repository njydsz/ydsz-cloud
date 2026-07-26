#!/usr/bin/env python3
"""Scan Java files for missing Javadoc comments."""
import pathlib
import re
import sys

BASE = pathlib.Path('ydsz-backend')
if not BASE.exists():
    BASE = pathlib.Path('.')

# Class/interface/enum/record declaration pattern
CLASS_DECL = re.compile(r'(public\s+)?(abstract\s+)?(class|interface|enum|record)\s+\w+')
# Method declaration pattern (rough)
METHOD_DECL = re.compile(
    r'(public|protected|private)\s+'
    r'(static\s+)?(final\s+)?(synchronized\s+)?'
    r'(?:[\w<>\[\],\s]+)\s+'
    r'(\w+)\s*\('
)


def check_javadoc_before(lines, line_idx, lookback=10):
    """Check if there's a Javadoc comment ending before the given line."""
    for j in range(line_idx - 1, max(line_idx - lookback, -1), -1):
        line = lines[j]
        if '*/' in line or '@author' in line or '@since' in line:
            return True
        # If we hit a non-comment, non-blank line, stop
        stripped = line.strip()
        if stripped and not stripped.startswith('*') and not stripped.startswith('//') and not stripped.startswith('/*') and '*/' not in stripped:
            return False
    return False


def scan_file(filepath):
    """Scan a single Java file for missing Javadoc."""
    content = filepath.read_text(encoding='utf-8', errors='ignore')
    lines = content.split('\n')
    issues = []

    for i, line in enumerate(lines):
        # Check class/interface/enum/enum declarations
        if CLASS_DECL.search(line):
            if not check_javadoc_before(lines, i):
                issues.append(f'  L{i+1}: Missing class Javadoc: {line.strip()[:80]}')

        # Check public method declarations (skip main, constructor, toString, etc.)
        m = METHOD_DECL.search(line)
        if m:
            method_name = m.group(5)
            # Skip common methods that may not need full Javadoc
            skip = {'toString', 'hashCode', 'equals', 'main', 'run',
                    'apply', 'accept', 'get', 'set', 'test'}
            if method_name in skip:
                continue
            # Skip getters/setters
            if re.match(r'^(get|set|is)[A-Z]\w*$', method_name):
                continue
            # Skip overridden methods (check for @Override above)
            has_override = False
            for k in range(i - 1, max(i - 3, -1), -1):
                if '@Override' in lines[k]:
                    has_override = True
                    break
            # Even @Override should have Javadoc per Alibaba standard,
            # but let's focus on non-override public methods first
            if not check_javadoc_before(lines, i):
                issues.append(f'  L{i+1}: Missing method Javadoc: {method_name}()')

    return issues


def main():
    modules = sorted([d for d in BASE.iterdir() if d.is_dir() and d.name.startswith('ydsz-')])

    grand_total = 0
    grand_issues = 0

    print('=' * 80)
    print('Javadoc Coverage Scan Report')
    print('=' * 80)

    for mod in modules:
        java_files = [f for f in mod.rglob('*.java') if 'target' not in f.parts]
        if not java_files:
            continue

        mod_issues = 0
        files_with_issues = 0

        for f in java_files:
            issues = scan_file(f)
            if issues:
                files_with_issues += 1
                mod_issues += len(issues)

        total = len(java_files)
        clean = total - files_with_issues
        pct = 100 * clean // max(total, 1)
        grand_total += total
        grand_issues += mod_issues

        status = 'OK' if mod_issues == 0 else '!!'
        print(f'[{status}] {mod.name:30s}: {total:4d} files, {files_with_issues:4d} with issues, {mod_issues:5d} total issues, {pct:3d}% clean')

    print('=' * 80)
    print(f'Total: {grand_total} Java files, {grand_issues} Javadoc issues')
    print('=' * 80)


if __name__ == '__main__':
    main()
