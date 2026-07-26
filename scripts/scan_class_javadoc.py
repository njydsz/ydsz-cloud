#!/usr/bin/env python3
"""Scan Java files for missing CLASS-level Javadoc (the most critical requirement)."""
import pathlib
import re

BASE = pathlib.Path('ydsz-backend')

# Match actual class/interface/enum/record declarations (not inside comments)
# Must start at beginning of line (with optional whitespace) and have access modifier or keyword
CLASS_DECL = re.compile(r'^\s*(public\s+|private\s+|protected\s+)?(abstract\s+|final\s+|sealed\s+|non-sealed\s+)*(class|interface|enum|record|@interface)\s+\w+')


def has_class_javadoc(filepath):
    """Check if a Java file has class-level Javadoc."""
    content = filepath.read_text(encoding='utf-8', errors='ignore')
    lines = content.split('\n')

    for i, line in enumerate(lines):
        if CLASS_DECL.search(line):
            # Look backwards for Javadoc closing */
            paren_depth = 0
            for j in range(i - 1, max(i - 50, -1), -1):
                l = lines[j]
                stripped = l.strip()
                # Found Javadoc closing - has Javadoc
                if '*/' in stripped:
                    return True
                # Count parentheses to handle multi-line annotations
                close_count = stripped.count(')')
                open_count = stripped.count('(')
                paren_depth += close_count - open_count
                # If we're inside annotation parentheses, skip
                if paren_depth > 0:
                    continue
                # Skip annotations, blank lines, and other non-class lines
                if (not stripped
                    or stripped.startswith('@')
                    or stripped.startswith(')')
                    or stripped.startswith('(')
                    or stripped.startswith(',')
                    or stripped.startswith('*')
                    or stripped.startswith('//')
                    or stripped.startswith('/*')
                    or stripped.startswith('import')
                    or stripped.startswith('package')
                    or stripped.startswith('}')
                    # Annotation parameter continuation lines
                    or '=' in stripped and not stripped.endswith(';')
                    or stripped.startswith('"')
                    or '.class' in stripped
                    or stripped.endswith(',')
                    or stripped.endswith('})')
                    or stripped.endswith(')')
                    or stripped.endswith('"')
                    or stripped.endswith('}')):
                    continue
                # Hit a non-comment, non-annotation line - no Javadoc
                return False
            return False
    # No class declaration found (e.g., package-info.java)
    return True


def main():
    modules = sorted([d for d in BASE.iterdir() if d.is_dir() and d.name.startswith('ydsz-')])

    grand_total = 0
    grand_missing = 0
    missing_files = {}

    print('=' * 80)
    print('Class-Level Javadoc Missing Scan')
    print('=' * 80)

    for mod in modules:
        java_files = [f for f in mod.rglob('*.java') if 'target' not in f.parts]
        if not java_files:
            continue

        missing = []
        for f in java_files:
            if not has_class_javadoc(f):
                missing.append(f)

        total = len(java_files)
        miss_count = len(missing)
        grand_total += total
        grand_missing += miss_count
        missing_files[mod.name] = missing

        status = 'OK' if miss_count == 0 else '!!'
        pct = 100 * (total - miss_count) // max(total, 1)
        print(f'[{status}] {mod.name:30s}: {total:4d} files, {miss_count:4d} missing class Javadoc, {pct:3d}% covered')

    print('=' * 80)
    print(f'Total: {grand_total} Java files, {grand_missing} missing class Javadoc')
    print('=' * 80)

    # Print details for modules with missing Javadoc
    for mod_name, files in sorted(missing_files.items()):
        if files:
            print(f'\n--- {mod_name} ({len(files)} files missing) ---')
            for f in files[:30]:
                # Use forward slashes to avoid escape sequence issues
                print(f'  {str(f).replace(chr(92), "/")}')
            if len(files) > 30:
                print(f'  ... and {len(files) - 30} more')


if __name__ == '__main__':
    main()
