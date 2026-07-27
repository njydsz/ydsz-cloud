#!/usr/bin/env python3
"""
Replace manual toVO methods in system ServiceImpl with MapStruct Converter calls.
Also replace BeanUtils.copyProperties if any.
"""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

FILES = [
    'ConfigServiceImpl.java',
    'DictItemServiceImpl.java',
    'DictServiceImpl.java',
    'DictVersionServiceImpl.java',
    'VariableServiceImpl.java',
    'AppInfoServiceImpl.java',
]

base_dir = os.path.join(BACKEND, 'ydsz-system', 'ydsz-system-server', 'src', 'main', 'java',
                       'com', 'njydsz', 'system', 'server', 'service', 'impl')

for fn in FILES:
    filepath = os.path.join(base_dir, fn)
    if not os.path.exists(filepath):
        print(f'Not found: {fn}')
        continue
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    
    # Add converter import if not present
    if 'SystemConverter' not in content:
        last_import = None
        for m in re.finditer(r'^import .+;$', content, re.MULTILINE):
            last_import = m
        if last_import:
            insert_pos = last_import.end()
            content = content[:insert_pos] + '\nimport com.njydsz.system.domain.converter.SystemConverter;' + content[insert_pos:]
    
    # Replace toVO method calls: toVO(entity) -> SystemConverter.INSTANT.entityToVO(entity)
    # But don't replace the method definition itself
    # Pattern: toVO(something) where it's a method call (not definition)
    # The method definition looks like: private XxVO toVO(Xx entity) {
    # The call looks like: return toVO(entity); or .map(this::toVO) or toVO(
    
    # Replace calls: toVO(xxx) -> SystemConverter.INSTANT.entityToVO(xxx)
    # But skip the definition line
    lines = content.split('\n')
    result = []
    in_tovo_method = False
    for line in lines:
        # Detect toVO method definition
        if re.match(r'\s*private\s+\w+VO\s+toVO\(', line):
            in_tovo_method = True
            result.append(line)
            continue
        
        if in_tovo_method:
            # Check for end of method (closing brace at same indent level as method)
            if line.strip() == '}':
                in_tovo_method = False
                # Replace the entire method with a delegation
                # Actually, let's just replace the method body to delegate to converter
                # Find the method signature from the result
                method_line = result[-1] if result else ''
                # Replace: private XxVO toVO(Xx entity) { ... }
                # -> private XxVO toVO(Xx entity) { return SystemConverter.INSTANT.entityToVO(entity); }
                # Actually, let's keep the method but change its body
                # The simplest approach: replace all toVO(entity) calls outside the method definition
                pass
            result.append(line)
            continue
        
        # Replace toVO calls
        if 'toVO(' in line and 'private' not in line and 'SystemConverter' not in line:
            # This is a call to toVO
            # Replace: toVO(xxx) -> SystemConverter.INSTANT.entityToVO(xxx)
            # But also handle this::toVO -> SystemConverter.INSTANT::entityToVO
            line = re.sub(r'\btoVO\(', 'SystemConverter.INSTANT.entityToVO(', line)
            line = line.replace('this::SystemConverter.INSTANT.entityToVO', 'SystemConverter.INSTANT::entityToVO')
        
        result.append(line)
    
    content = '\n'.join(result)
    
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f'Fixed: {fn}')
    else:
        print(f'No changes: {fn}')

print('\nDone!')
