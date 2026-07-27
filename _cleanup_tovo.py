#!/usr/bin/env python3
"""
Clean up toVO methods from Service classes.
1. Delete dead code methods (no calls)
2. Replace calls with Converter then delete method
"""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

# Module -> Converter class name mapping
MODULE_CONVERTERS = {
    'nextwiki': 'NextwikiConverter',
    'system': 'SystemConverter',
    'userinfo': 'UserInfoConverter',
}

# Files with dead code (no calls) - just delete the method
DEAD_CODE_FILES = [
    'ydsz-system/ydsz-system-server/src/main/java/com/njydsz/system/server/service/impl/AppInfoServiceImpl.java',
    'ydsz-system/ydsz-system-server/src/main/java/com/njydsz/system/server/service/impl/ConfigServiceImpl.java',
    'ydsz-system/ydsz-system-server/src/main/java/com/njydsz/system/server/service/impl/DictItemServiceImpl.java',
    'ydsz-system/ydsz-system-server/src/main/java/com/njydsz/system/server/service/impl/DictServiceImpl.java',
    'ydsz-system/ydsz-system-server/src/main/java/com/njydsz/system/server/service/impl/DictVersionServiceImpl.java',
    'ydsz-system/ydsz-system-server/src/main/java/com/njydsz/system/server/service/impl/VariableServiceImpl.java',
    'ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/CompanyServiceImpl.java',
]

# Files with calls - replace calls then delete method
FILES_WITH_CALLS = [
    'ydsz-nextwiki/ydsz-nextwiki-server/src/main/java/com/njydsz/nextwiki/server/service/FileApplicationService.java',
    'ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/DepartmentServiceImpl.java',
    'ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/LanguageServiceImpl.java',
    'ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/MenuServiceImpl.java',
    'ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/PostServiceImpl.java',
    'ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/RoleServiceImpl.java',
    'ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/UserAccountServiceImpl.java',
]

def find_method_range(content, method_name='toVO'):
    """Find the start and end of a method definition."""
    # Find the method declaration
    pattern = rf'\n\s+(private|public|protected)\s+\w+\s+{method_name}\s*\('
    m = re.search(pattern, content)
    if not m:
        return None, None
    
    # Find the start of the method (go back to the line start)
    start = content.rfind('\n', 0, m.start()) + 1
    
    # Find the matching closing brace
    # Start from the opening brace after the method signature
    brace_start = content.find('{', m.end())
    if brace_start == -1:
        return None, None
    
    depth = 1
    pos = brace_start + 1
    while pos < len(content) and depth > 0:
        if content[pos] == '{':
            depth += 1
        elif content[pos] == '}':
            depth -= 1
        pos += 1
    
    # Find the end of line after closing brace
    end = pos
    # Include trailing newline
    if end < len(content) and content[end] == '\n':
        end += 1
    if end < len(content) and content[end] == '\n':
        end += 1
    
    return start, end

def delete_tovo_method(content):
    """Delete the toVO method from content."""
    start, end = find_method_range(content)
    if start is None:
        return content, False
    return content[:start] + content[end:], True

def replace_tovo_calls(content, converter_name):
    """Replace toVO(...) calls with Converter.INSTANT.entityToVO(...)."""
    # Replace patterns like: toVO(something) but NOT method declarations
    # Pattern: word boundary toVO( not preceded by private/public/protected
    def replace_call(m):
        prefix = m.group(1)  # anything before toVO
        args = m.group(2)     # arguments
        return f'{prefix}{converter_name}.INSTANT.entityToVO({args})'
    
    # Match: return toVO( or = toVO( etc. but not private/public/protected ... toVO(
    content = re.sub(
        r'(?<!private\s)(?<!public\s)(?<!protected\s)\btoVO\(([^)]+)\)',
        lambda m: f'{converter_name}.INSTANT.entityToVO({m.group(1)})',
        content
    )
    return content

# Process dead code files
print("=== Processing dead code files ===")
for rel_path in DEAD_CODE_FILES:
    fp = os.path.join(BACKEND, rel_path.replace('/', os.sep))
    with open(fp, 'r', encoding='utf-8') as f:
        content = f.read()
    
    new_content, deleted = delete_tovo_method(content)
    if deleted:
        with open(fp, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"  Deleted toVO: {rel_path}")
    else:
        print(f"  NOT FOUND: {rel_path}")

# Process files with calls
print("\n=== Processing files with calls ===")
for rel_path in FILES_WITH_CALLS:
    fp = os.path.join(BACKEND, rel_path.replace('/', os.sep))
    with open(fp, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Determine module name
    module = rel_path.split('/')[0].replace('ydsz-', '')
    converter_name = MODULE_CONVERTERS.get(module)
    if not converter_name:
        print(f"  SKIP (no converter): {rel_path}")
        continue
    
    # Replace calls first
    new_content = replace_tovo_calls(content, converter_name)
    
    # Then delete the method
    new_content, deleted = delete_tovo_method(new_content)
    
    if deleted or new_content != content:
        with open(fp, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"  Updated: {rel_path} (calls replaced + method deleted)")
    else:
        print(f"  NO CHANGES: {rel_path}")

print("\n=== Done! ===")
