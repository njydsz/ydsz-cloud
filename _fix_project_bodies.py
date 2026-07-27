#!/usr/bin/env python3
"""
Fix remaining controller method bodies that weren't caught by the previous script.
Handles: inline one-liner methods, IPage patterns, PageResponse patterns.
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
    
    # Pattern 1: Inline single entity return on same line as method
    # public BaseResponse<EntityVO> method(...) { return BaseResponse.success(service.xxx(...)); }
    # -> public BaseResponse<EntityVO> method(...) { return BaseResponse.success(Converter.INSTANT.entityToVO(service.xxx(...))); }
    
    # Find: BaseResponse<EntityVO> ... { return BaseResponse.success(xxxService.something(args)); }
    # where Converter.INSTANT is not already present
    def fix_inline_single(m):
        full = m.group(0)
        if 'Converter.INSTANT' in full:
            return full
        ret_type = m.group(1)  # EntityVO
        entity_name = ret_type.replace('VO', '')
        service_call = m.group(2)
        return f'BaseResponse<{ret_type}> {m.group(0)[len("BaseResponse<" + ret_type + ">"):m.group(0).index("{ return BaseResponse.success(")]}{{ return BaseResponse.success({converter}.INSTANT.entityToVO({service_call})); }}'
    
    # Actually let's use a simpler approach - find return statements with service calls
    # that don't already have Converter.INSTANT
    
    # Pattern: return BaseResponse.success(service.xxx(args)); on same line
    # We need to know the method return type to decide if we should wrap
    
    # Better approach: find all patterns where BaseResponse<EntityVO> is the return type
    # and the body has a direct service call without Converter wrapping
    
    # Pattern A: One-liner: ... { return BaseResponse.success(xxx); }
    for entity_vo in re.findall(r'BaseResponse<(\w+VO)>', content):
        entity_name = entity_vo.replace('VO', '')
        list_method = get_list_method_name(entity_name)
        
        # Skip if already has Converter
        if f'{converter}.INSTANT' in content:
            # Check if ALL returns are wrapped
            pass
        
        # Fix one-liner single entity: { return BaseResponse.success(service.getById(id)); }
        pattern = rf'(\{{\s*return\s+BaseResponse\.success\()([^)]+)(\);)\s*\}})'
        def replacer(m):
            full = m.group(0)
            if 'Converter.INSTANT' in full:
                return full
            service_call = m.group(2)
            return f'{{ return BaseResponse.success({converter}.INSTANT.entityToVO({service_call})); }}'
        content = re.sub(pattern, replacer, content)
    
    # Pattern B: IPage pattern
    # IPage<Entity> r = service.page(p, s);
    # return PageResponse.success(r.getRecords(), r.getTotal(), ...);
    # -> IPage<Entity> r = service.page(p, s);
    #    return PageResponse.success(Converter.INSTANT.xxxListToVO(r.getRecords()), r.getTotal(), ...);
    
    for entity_vo in re.findall(r'PageResponse<(\w+VO)>', content):
        entity_name = entity_vo.replace('VO', '')
        list_method = get_list_method_name(entity_name)
        
        # Find: PageResponse.success(r.getRecords(), r.getTotal(),
        pattern = rf'PageResponse\.success\((\w+)\.getRecords\(\)'
        def replacer(m):
            var = m.group(1)
            full_match = m.group(0)
            if f'{converter}.INSTANT' in content[max(0, m.start()-50):m.end()+50]:
                return full_match
            return f'PageResponse.success({converter}.INSTANT.{list_method}({var}.getRecords()'
        content = re.sub(pattern, replacer, content)
    
    # Pattern C: return BaseResponse.success(xxxService.list()); for List<EntityVO>
    for entity_vo in re.findall(r'BaseResponse<List<(\w+VO)>>', content):
        entity_name = entity_vo.replace('VO', '')
        list_method = get_list_method_name(entity_name)
        
        pattern = r'(return\s+BaseResponse\.success\()([^;]+)(\);)'
        def replacer(m):
            full = m.group(0)
            if 'Converter.INSTANT' in full:
                return full
            service_call = m.group(2)
            return f'return BaseResponse.success({converter}.INSTANT.{list_method}({service_call}));'
        # Only apply to lines that don't already have Converter
        lines = content.split('\n')
        for i, line in enumerate(lines):
            if 'BaseResponse<List<' + entity_vo + '>>' not in content:
                continue
            # Check if this line is in a method that returns List<EntityVO>
            # Simple heuristic: if the line has "return BaseResponse.success(" and no Converter
            if 'return BaseResponse.success(' in line and 'Converter.INSTANT' not in line:
                # Don't touch if it's a Boolean/String return
                if 'service.save(' in line or 'service.update' in line or 'service.remove' in line:
                    continue
                lines[i] = re.sub(
                    r'(return\s+BaseResponse\.success\()([^;]+)(\);)',
                    lambda m: f'return BaseResponse.success({converter}.INSTANT.{list_method}({m.group(2)}));' if 'Converter.INSTANT' not in m.group(0) else m.group(0),
                    line
                )
        content = '\n'.join(lines)
    
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
