#!/usr/bin/env python3
"""Debug scanner for specific file."""
import pathlib
import re

BASE = pathlib.Path('d:/Code/ydsz/ydsz-pmis/ydsz-backend')

CLASS_DECL = re.compile(r'^\s*(public\s+|private\s+|protected\s+)?(abstract\s+|final\s+|sealed\s+|non-sealed\s+)*(class|interface|enum|record|@interface)\s+\w+')


def has_class_javadoc(filepath):
    """Check if a Java file has class-level Javadoc."""
    content = filepath.read_text(encoding='utf-8', errors='ignore')
    lines = content.split('\n')

    for i, line in enumerate(lines):
        if CLASS_DECL.search(line):
            print(f'  Found class decl at line {i}: {line.strip()}')
            paren_depth = 0
            for j in range(i - 1, max(i - 50, -1), -1):
                l = lines[j]
                stripped = l.strip()
                print(f'    [{j}] stripped={repr(stripped)}')
                if '*/' in stripped:
                    print(f'    -> Found */ returning True')
                    return True
                close_count = stripped.count(')')
                open_count = stripped.count('(')
                paren_depth += close_count - open_count
                if paren_depth > 0:
                    print(f'    -> paren_depth={paren_depth}, skip')
                    continue
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
                    or '=' in stripped and not stripped.endswith(';')
                    or stripped.startswith('"')
                    or '.class' in stripped
                    or stripped.endswith(',')
                    or stripped.endswith('})')
                    or stripped.endswith(')')
                    or stripped.endswith('"')
                    or stripped.endswith('}')):
                    print(f'    -> skip (annotation/blank/etc)')
                    continue
                print(f'    -> Hit non-comment line, returning False')
                return False
            print(f'  -> Exhausted backward search, returning False')
            return False
    return True


# Test DefaultStorageFactory
f = BASE / 'ydsz-common/ydsz-common-file/src/main/java/com/njydsz/common/file/storage/DefaultStorageFactory.java'
print(f'Testing: {f}')
print(f'Exists: {f.exists()}')
result = has_class_javadoc(f)
print(f'Result: {result}')
print()

# Test WebSocketConfigurer
f2 = BASE / 'ydsz-common/ydsz-common-socket/src/main/java/com/njydsz/common/socket/config/WebSocketConfigurer.java'
print(f'Testing: {f2}')
print(f'Exists: {f2.exists()}')
result2 = has_class_javadoc(f2)
print(f'Result: {result2}')
