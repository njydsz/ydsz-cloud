#!/usr/bin/env python3
"""
Auto-fix controllers: replace Entity return types with VO, add Converter calls.
Handles: BaseResponse<Entity>, BaseResponse<Page<Entity>>, BaseResponse<List<Entity>>
"""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

# Converter name per module
CONVERTER_MAP = {
    'cronjob': 'CronjobConverter',
    'workflow': 'WorkflowConverter',
    'project': 'ProjectConverter',
    'literule': 'LiteruleConverter',
    'agent': 'AgentConverter',
}

# Converter package per module
CONV_PKG = {
    'cronjob': 'com.njydsz.cronjob.domain.converter',
    'workflow': 'com.njydsz.workflow.domain.converter',
    'project': 'com.njydsz.project.domain.converter',
    'literule': 'com.njydsz.literule.domain.converter',
    'agent': 'com.njydsz.agent.domain.converter',
}

# VO package per module
VO_PKG = {
    'cronjob': 'com.njydsz.cronjob.domain.vo',
    'workflow': 'com.njydsz.workflow.domain.vo',
    'project': 'com.njydsz.project.domain.vo',
    'literule': 'com.njydsz.literule.domain.vo',
    'agent': 'com.njydsz.agent.domain.vo',
}

MODULES = [
    ('cronjob', 'ydsz-cronjob'),
    ('workflow', 'ydsz-workflow'),
    ('project', 'ydsz-project'),
    ('literule', 'ydsz-literule'),
    ('agent', 'ydsz-agent'),
]

def fix_controller(filepath, mod_key):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    converter_name = CONVERTER_MAP[mod_key]
    conv_pkg = CONV_PKG[mod_key]
    vo_pkg = VO_PKG[mod_key]
    
    # Find all Entity types used in return types
    entity_types = set()
    
    # Pattern 1: BaseResponse<EntityType> (single)
    for m in re.finditer(r'BaseResponse<(\w+)>', content):
        t = m.group(1)
        if t not in ('String', 'Boolean', 'Integer', 'Long', 'Void', 'Object', 'Map', 'List', 'Page', 'BaseResponse', 'PageResponse'):
            entity_types.add(t)
    
    # Pattern 2: BaseResponse<Page<EntityType>>
    for m in re.finditer(r'BaseResponse<Page<(\w+)>>', content):
        entity_types.add(m.group(1))
    
    # Pattern 3: BaseResponse<List<EntityType>>
    for m in re.finditer(r'BaseResponse<List<(\w+)>>', content):
        entity_types.add(m.group(1))
    
    # Pattern 4: PageResponse<EntityType>
    for m in re.finditer(r'PageResponse<(\w+)>', content):
        t = m.group(1)
        if t not in ('String', 'Boolean', 'Integer', 'Long', 'Void', 'Object', 'Map', 'List', 'Page'):
            entity_types.add(t)
    
    if not entity_types:
        return False
    
    # Add imports
    imports_to_add = set()
    imports_to_add.add(f'import {conv_pkg}.{converter_name};')
    for et in entity_types:
        imports_to_add.add(f'import {vo_pkg}.{et}VO;')
    
    # Check which imports already exist
    for imp in list(imports_to_add):
        if imp in content:
            imports_to_add.remove(imp)
    
    if imports_to_add:
        # Find last import line
        last_import = None
        for m in re.finditer(r'^import .+;$', content, re.MULTILINE):
            last_import = m
        if last_import:
            insert_pos = last_import.end()
            new_imports = '\n' + '\n'.join(sorted(imports_to_add))
            content = content[:insert_pos] + new_imports + content[insert_pos:]
    
    # Replace return types and method bodies
    for et in entity_types:
        vo = et + 'VO'
        # Determine list method name
        if et.endswith('DO'):
            base_name = et[:-2]
        else:
            base_name = et
        list_method = base_name[0].lower() + base_name[1:] + 'ListToVO'
        
        # Pattern 1: BaseResponse<Page<Entity>> - need to wrap with converter
        # Replace type: BaseResponse<Page<Entity>> -> BaseResponse<Page<EntityVO>>
        content = content.replace(f'BaseResponse<Page<{et}>>', f'BaseResponse<Page<{vo}>>')
        
        # Pattern 2: BaseResponse<List<Entity>> -> BaseResponse<List<EntityVO>>
        content = content.replace(f'BaseResponse<List<{et}>>', f'BaseResponse<List<{vo}>>')
        
        # Pattern 3: BaseResponse<Entity> -> BaseResponse<EntityVO> (but not Page/List which are already handled)
        content = re.sub(rf'BaseResponse<{et}>(?!\w)', f'BaseResponse<{vo}>', content)
        
        # Pattern 4: PageResponse<Entity> -> PageResponse<EntityVO>
        content = content.replace(f'PageResponse<{et}>', f'PageResponse<{vo}>')
    
    # Now fix method bodies - this is more complex and needs per-pattern handling
    # We need to wrap service calls with converter
    
    # Pattern A: return BaseResponse.success(service.method(...));
    # -> Page<Entity> page = service.method(...);
    #    Page<EntityVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
    #    voPage.setRecords(Converter.INSTANT.xxxListToVO(page.getRecords()));
    #    return BaseResponse.success(voPage);
    
    # This is too complex for regex. We'll handle the simple cases:
    # return BaseResponse.success(xxxService.getById(id)); -> return BaseResponse.success(Converter.INSTANT.entityToVO(xxxService.getById(id)));
    # return BaseResponse.success(xxxService.list()); -> return BaseResponse.success(Converter.INSTANT.xxxListToVO(xxxService.list()));
    
    # For single entity returns: wrap with entityToVO
    for et in entity_types:
        vo = et + 'VO'
        if et.endswith('DO'):
            base_name = et[:-2]
        else:
            base_name = et
        list_method = base_name[0].lower() + base_name[1:] + 'ListToVO'
        
        # Replace: return BaseResponse.success(service.method(...));
        # where return type is BaseResponse<vo> (single entity)
        # We need to find: BaseResponse.success(something) where the method return type is BaseResponse<EntityVO>
        
        # Simple pattern: return BaseResponse.success(xxxService.method(args));
        # -> return BaseResponse.success(Converter.INSTANT.entityToVO(xxxService.method(args)));
        
        # For list returns: BaseResponse<List<EntityVO>>
        # return BaseResponse.success(xxxService.list(...));
        # -> return BaseResponse.success(Converter.INSTANT.xxxListToVO(xxxService.list(...)));
        
        pass  # Will be handled by the more specific script below
    
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

# Process all modules
for mod_key, mod_dir in MODULES:
    web_path = os.path.join(BACKEND, mod_dir, f'ydsz-{mod_key}-web', 'src', 'main', 'java')
    if not os.path.exists(web_path):
        continue
    
    print(f'\n=== {mod_key} ===')
    for root, dirs, files in os.walk(web_path):
        for fn in files:
            if fn.endswith('.java') and 'Controller' in fn:
                fp = os.path.join(root, fn)
                if fix_controller(fp, mod_key):
                    print(f'  Fixed imports/types: {fn}')

print('\nDone with type fixes. Now need to fix method bodies.')
