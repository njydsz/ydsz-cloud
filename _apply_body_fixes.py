#!/usr/bin/env python3
"""
Fix method bodies in controllers:
1. Single entity: return BaseResponse.success(service.method(...)) -> return BaseResponse.success(Converter.INSTANT.entityToVO(service.method(...)))
2. List entity: return BaseResponse.success(service.method(...)) -> return BaseResponse.success(Converter.INSTANT.xxxListToVO(service.method(...)))
3. Page entity: return BaseResponse.success(service.method(...)) -> wrap with Page conversion
"""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

CONVERTER_MAP = {
    'cronjob': 'CronjobConverter',
    'workflow': 'WorkflowConverter',
    'project': 'ProjectConverter',
    'literule': 'LiteruleConverter',
    'agent': 'AgentConverter',
}

MODULES = [
    ('cronjob', 'ydsz-cronjob'),
    ('workflow', 'ydsz-workflow'),
    ('project', 'ydsz-project'),
    ('literule', 'ydsz-literule'),
    ('agent', 'ydsz-agent'),
]

def get_list_method_name(entity_name):
    """Get the list conversion method name from entity class name."""
    if entity_name.endswith('DO'):
        base = entity_name[:-2]
    else:
        base = entity_name
    return base[0].lower() + base[1:] + 'ListToVO'

def fix_controller_bodies(filepath, mod_key):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    converter = CONVERTER_MAP[mod_key]
    
    # Find all method signatures with their return types and bodies
    # Pattern: public BaseResponse<XxxVO> methodName(...) {
    #     ... body ...
    # }
    
    # Strategy: Find each method that returns BaseResponse<EntityVO> or BaseResponse<Page<EntityVO>>
    # or BaseResponse<List<EntityVO>> and fix the return statements
    
    # 1. Fix single entity returns:
    # return BaseResponse.success(xxxService.something(...));
    # -> return BaseResponse.success(Converter.INSTANT.entityToVO(xxxService.something(...)));
    
    # But only when the method signature returns BaseResponse<EntityVO>
    
    # Find all method declarations
    method_pattern = re.compile(
        r'(public\s+BaseResponse<([^>]+)>\s+(\w+)\s*\([^)]*\)\s*\{[^}]*\})',
        re.DOTALL
    )
    
    # Actually, let's use a simpler approach:
    # Find return statements and check the method return type
    
    # Split content into methods
    lines = content.split('\n')
    result_lines = []
    current_method_return_type = None
    current_method_entity_vo = None
    current_method_page_entity = None
    current_method_list_entity = None
    
    i = 0
    while i < len(lines):
        line = lines[i]
        
        # Detect method signature with EntityVO return type
        # Pattern: public BaseResponse<EntityVO> methodName(
        m = re.match(r'\s*public\s+BaseResponse<(\w+VO)>\s+(\w+)\s*\(', line)
        if m:
            current_method_return_type = m.group(1)  # e.g., JobVO
            current_method_entity_vo = m.group(1)
            current_method_page_entity = None
            current_method_list_entity = None
            result_lines.append(line)
            i += 1
            continue
        
        # Pattern: public BaseResponse<Page<EntityVO>> methodName(
        m = re.match(r'\s*public\s+BaseResponse<Page<(\w+VO)>>\s+(\w+)\s*\(', line)
        if m:
            current_method_return_type = 'Page<' + m.group(1) + '>'
            current_method_page_entity = m.group(1)  # e.g., JobVO
            current_method_entity_vo = None
            current_method_list_entity = None
            result_lines.append(line)
            i += 1
            continue
        
        # Pattern: public BaseResponse<List<EntityVO>> methodName(
        m = re.match(r'\s*public\s+BaseResponse<List<(\w+VO)>>\s+(\w+)\s*\(', line)
        if m:
            current_method_return_type = 'List<' + m.group(1) + '>'
            current_method_list_entity = m.group(1)  # e.g., JobVO
            current_method_entity_vo = None
            current_method_page_entity = None
            result_lines.append(line)
            i += 1
            continue
        
        # Pattern: public PageResponse<EntityVO> methodName(
        m = re.match(r'\s*public\s+PageResponse<(\w+VO)>\s+(\w+)\s*\(', line)
        if m:
            current_method_return_type = 'PageResponse<' + m.group(1) + '>'
            current_method_page_entity = m.group(1)
            current_method_entity_vo = None
            current_method_list_entity = None
            result_lines.append(line)
            i += 1
            continue
        
        # Check for closing brace to reset method context
        if line.strip() == '}':
            current_method_return_type = None
            current_method_entity_vo = None
            current_method_page_entity = None
            current_method_list_entity = None
            result_lines.append(line)
            i += 1
            continue
        
        # Fix return statements based on method context
        if current_method_entity_vo:
            # Single entity: wrap with entityToVO
            # Pattern: return BaseResponse.success(xxxService.something(...));
            # But NOT if it already has Converter.INSTANT
            if 'Converter.INSTANT' not in line and 'BaseResponse.success(' in line:
                line = re.sub(
                    r'return BaseResponse\.success\(([^;]+)\);',
                    lambda m: f'return BaseResponse.success({converter}.INSTANT.entityToVO({m.group(1)}));',
                    line
                )
            # Also handle: return BaseResponse.error(...)
            # Don't touch error returns
        
        elif current_method_list_entity:
            # List entity: wrap with xxxListToVO
            vo_name = current_method_list_entity  # e.g., JobVO
            entity_name = vo_name.replace('VO', '')
            list_method = get_list_method_name(entity_name)
            if 'Converter.INSTANT' not in line and 'BaseResponse.success(' in line and 'return' in line:
                line = re.sub(
                    r'return BaseResponse\.success\(([^;]+)\);',
                    lambda m: f'return BaseResponse.success({converter}.INSTANT.{list_method}({m.group(1)}));',
                    line
                )
        
        elif current_method_page_entity:
            # Page entity: need to convert
            vo_name = current_method_page_entity  # e.g., JobVO
            entity_name = vo_name.replace('VO', '')
            list_method = get_list_method_name(entity_name)
            
            # Pattern: return BaseResponse.success(xxxService.page(query));
            # -> Page<Entity> page = xxxService.page(query);
            #    Page<EntityVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            #    voPage.setRecords(Converter.INSTANT.xxxListToVO(page.getRecords()));
            #    return BaseResponse.success(voPage);
            if 'Converter.INSTANT' not in line and 'BaseResponse.success(' in line and 'return' in line:
                # Extract the service call
                m = re.search(r'return BaseResponse\.success\(([^;]+)\);', line)
                if m:
                    service_call = m.group(1)
                    indent = len(line) - len(line.lstrip())
                    indent_str = ' ' * indent
                    entity_name_no_vo = entity_name
                    new_lines = [
                        f'{indent_str}Page<{entity_name_no_vo}> page = {service_call};',
                        f'{indent_str}Page<{vo_name}> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());',
                        f'{indent_str}voPage.setRecords({converter}.INSTANT.{list_method}(page.getRecords()));',
                        f'{indent_str}return BaseResponse.success(voPage);',
                    ]
                    result_lines.extend(new_lines)
                    i += 1
                    continue
        
        result_lines.append(line)
        i += 1
    
    new_content = '\n'.join(result_lines)
    
    if new_content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    return False

# Process all modules
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
                if fix_controller_bodies(fp, mod_key):
                    fixed_count += 1
                    print(f'  Fixed: {fn}')
    print(f'  Total: {fixed_count} controllers fixed')

print('\nDone!')
