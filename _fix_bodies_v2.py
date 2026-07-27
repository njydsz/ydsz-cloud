#!/usr/bin/env python3
"""
Fix controller method bodies - reliable line-by-line approach.
Handles three patterns:
1. Single entity: return BaseResponse.success(service.xxx(...)); -> wrap with entityToVO
2. IPage pattern: r = service.page(...); ... PageResponse.success(r.getRecords(),...) -> wrap getRecords
3. List entity: return BaseResponse.success(service.list(...)); -> wrap with xxxListToVO
"""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

MODULES = [
    ('cronjob', 'ydsz-cronjob'),
    ('workflow', 'ydsz-workflow'),
    ('project', 'ydsz-project'),
    ('literule', 'ydsz-literule'),
    ('agent', 'ydsz-agent'),
]

CONVERTER_MAP = {
    'cronjob': 'CronjobConverter',
    'workflow': 'WorkflowConverter',
    'project': 'ProjectConverter',
    'literule': 'LiteruleConverter',
    'agent': 'AgentConverter',
}

def get_list_method_name(entity_name):
    if entity_name.endswith('DO'):
        base = entity_name[:-2]
    else:
        base = entity_name
    return base[0].lower() + base[1:] + 'ListToVO'

def fix_controller(filepath, mod_key):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    converter = CONVERTER_MAP[mod_key]
    
    # 1. Fix IPage pattern: PageResponse.success(r.getRecords(), r.getTotal(), ...) 
    # -> PageResponse.success(Converter.INSTANT.xxxListToVO(r.getRecords()), r.getTotal(), ...)
    # Find all PageResponse<EntityVO> to know which entity types to fix
    for entity_vo in set(re.findall(r'PageResponse<(\w+VO)>', content)):
        entity_name = entity_vo.replace('VO', '')
        list_method = get_list_method_name(entity_name)
        
        # Pattern: PageResponse.success(VAR.getRecords(),
        # Replace with: PageResponse.success(Converter.INSTANT.listMethod(VAR.getRecords()),
        for var_match in re.finditer(r'PageResponse\.success\((\w+)\.getRecords\(\),', content):
            var = var_match.group(1)
            old = f'PageResponse.success({var}.getRecords(),'
            new = f'PageResponse.success({converter}.INSTANT.{list_method}({var}.getRecords()),'
            if old in content and new not in content:
                content = content.replace(old, new)
    
    # 2. Fix inline one-liner single entity returns:
    # { return BaseResponse.success(service.getById(id)); }
    # -> { return BaseResponse.success(Converter.INSTANT.entityToVO(service.getById(id))); }
    # But only for methods returning BaseResponse<EntityVO>
    
    # Find method signatures and their bodies on the same line
    # Pattern: BaseResponse<EntityVO> method(...) { return BaseResponse.success(call); }
    pattern = re.compile(r'BaseResponse<(\w+VO)>\s+\w+\([^)]*\)\s*\{\s*return\s+BaseResponse\.success\(([^)]+(?:\([^)]*\))*[^)]*)\);\s*\}')
    
    def fix_one_liner(m):
        entity_vo = m.group(1)
        service_call = m.group(2)
        full = m.group(0)
        if 'Converter.INSTANT' in full:
            return full
        # Reconstruct
        prefix = full[:full.index('{ return BaseResponse.success(')]
        return f'{prefix}{{ return BaseResponse.success({converter}.INSTANT.entityToVO({service_call})); }}'
    
    content = pattern.sub(fix_one_liner, content)
    
    # 3. Fix multi-line single entity returns:
    # return BaseResponse.success(service.getById(id));
    # -> return BaseResponse.success(Converter.INSTANT.entityToVO(service.getById(id)));
    # We need context of the method return type
    
    # Find: return BaseResponse.success(SOMETHING); where SOMETHING doesn't contain Converter.INSTANT
    # and the method returns BaseResponse<EntityVO>
    
    # Use a state machine: track current method return type
    lines = content.split('\n')
    result = []
    current_return_vo = None
    current_list_vo = None
    
    for line in lines:
        # Detect method signature
        m = re.search(r'BaseResponse<(\w+VO)>\s+\w+\s*\(', line)
        if m:
            current_return_vo = m.group(1)
            current_list_vo = None
            result.append(line)
            continue
        
        m = re.search(r'BaseResponse<List<(\w+VO)>>\s+\w+\s*\(', line)
        if m:
            current_list_vo = m.group(1)
            current_return_vo = None
            result.append(line)
            continue
        
        # Reset on closing brace
        if line.strip() == '}':
            current_return_vo = None
            current_list_vo = None
            result.append(line)
            continue
        
        # Fix single entity return
        if current_return_vo and 'Converter.INSTANT' not in line:
            if 'return BaseResponse.success(' in line and 'service.save(' not in line and 'service.update' not in line and 'service.remove' not in line and 'Boolean' not in line:
                # Extract the service call
                m = re.search(r'return BaseResponse\.success\((.+)\);', line)
                if m:
                    service_call = m.group(1)
                    indent = len(line) - len(line.lstrip())
                    result.append(' ' * indent + f'return BaseResponse.success({converter}.INSTANT.entityToVO({service_call}));')
                    continue
        
        # Fix list entity return
        if current_list_vo and 'Converter.INSTANT' not in line:
            if 'return BaseResponse.success(' in line:
                entity_name = current_list_vo.replace('VO', '')
                list_method = get_list_method_name(entity_name)
                m = re.search(r'return BaseResponse\.success\((.+)\);', line)
                if m:
                    service_call = m.group(1)
                    indent = len(line) - len(line.lstrip())
                    result.append(' ' * indent + f'return BaseResponse.success({converter}.INSTANT.{list_method}({service_call}));')
                    continue
        
        result.append(line)
    
    content = '\n'.join(result)
    
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

for mod_key, mod_dir in MODULES:
    web_path = os.path.join(BACKEND, mod_dir, f'ydsz-{mod_key}-web', 'src', 'main', 'java')
    if not os.path.exists(web_path):
        continue
    
    print(f'\n=== {mod_key} ===')
    fixed_count = 0
    for root, dirs, files in os.walk(web_path):
        for fn in files:
            if fn.endswith('.java') and 'Controller' in fn:
                fp = os.path.join(root, fn)
                if fix_controller(fp, mod_key):
                    fixed_count += 1
                    print(f'  Fixed: {fn}')
    print(f'  Total: {fixed_count}')

print('\nDone!')
