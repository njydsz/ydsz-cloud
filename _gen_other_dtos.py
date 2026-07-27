#!/usr/bin/env python3
"""
Generate PostDTO/PutDTO for cronjob, literule, agent modules.
Update controllers and converters.
"""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

def parse_entity_fields(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    fields = []
    base_fields = {'id', 'deleted', 'revision', 'tenantId', 'createdBy', 'createdAt', 'updatedBy', 'updatedAt', 'status', 'serialVersionUID'}
    for m in re.finditer(r'private\s+([\w<>\[\].]+)\s+(\w+)\s*;', content):
        ftype = m.group(1)
        fname = m.group(2)
        if fname in base_fields:
            continue
        fields.append((ftype, fname))
    return fields

def find_entity_file(module_key, entity_name):
    """Find entity file in module domain."""
    domain_path = os.path.join(BACKEND, f'ydsz-{module_key}', f'ydsz-{module_key}-domain', 'src', 'main', 'java')
    for root, dirs, files in os.walk(domain_path):
        for fn in files:
            if fn == entity_name + '.java':
                return os.path.join(root, fn)
    return None

def generate_dto(entity_name, fields, module_key, dto_type):
    """Generate PostDTO or PutDTO."""
    is_post = dto_type == 'post'
    dto_cls = entity_name + ('PostDTO' if is_post else 'PutDTO')
    pkg = f'com.njydsz.{module_key}.domain.dto.{dto_type}'
    
    imports = set()
    for ftype, _ in fields:
        if 'LocalDateTime' in ftype:
            imports.add('java.time.LocalDateTime')
        elif 'LocalDate' in ftype:
            imports.add('java.time.LocalDate')
        elif 'BigDecimal' in ftype:
            imports.add('java.math.BigDecimal')
    
    lines = [f'package {pkg};', '']
    if imports:
        for imp in sorted(imports):
            lines.append(f'import {imp};')
        lines.append('')
    lines.extend([
        'import java.io.Serial;',
        'import java.io.Serializable;',
        'import lombok.Data;',
        '',
        '/**',
        f' * {entity_name} {"新增" if is_post else "修改"}请求 DTO。',
        ' *',
        ' * @author ydsz-team',
        ' * @since 1.0.0',
        ' */',
        '@Data',
        f'public class {dto_cls} implements Serializable {{',
        '',
        '    @Serial',
        '    private static final long serialVersionUID = 1L;',
        '',
    ])
    if not is_post:
        lines.append('    private String id;')
    for ftype, fname in fields:
        lines.append(f'    private {ftype} {fname};')
    lines.append('}')
    return '\n'.join(lines), pkg, dto_cls

# =============================================
# Module configs: (module_key, entities_to_process)
# entities_to_process: list of (entity_name, is_post_only, is_put_only)
# =============================================

MODULES_CONFIG = {
    'cronjob': {
        'entities': [
            ('JobWebhook', True, True),  # needs both PostDTO and PutDTO
        ],
        'converter_name': 'CronjobConverter',
    },
    'agent': {
        'entities': [
            ('AgentDefinitionDO', True, True),
        ],
        'converter_name': 'AgentConverter',
    },
    'literule': {
        'entities': [
            ('RuleDefinitionDO', True, False),  # POST only (save)
            ('RuleTestCaseDO', True, False),    # POST only (saveTestCase)
            ('DecisionTable', True, False),     # POST only (saveDecisionTable)
            ('RuleChainGraphDO', True, False),  # POST only (saveChainGraph)
            ('RuleABPolicy', False, True),      # PUT only (updateABPolicy)
            ('RulePackDO', True, False),        # POST only (publishPack)
        ],
        'converter_name': 'LiteruleConverter',
    },
}

IGNORE_FULL = '''    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)'''

IGNORE_NO_ID = '''    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)'''

for mod_key, config in MODULES_CONFIG.items():
    print(f"\n{'='*60}")
    print(f"Processing module: {mod_key}")
    print(f"{'='*60}")
    
    domain_path = os.path.join(BACKEND, f'ydsz-{mod_key}', f'ydsz-{mod_key}-domain', 'src', 'main', 'java',
                               'com', 'njydsz', mod_key, 'domain')
    
    all_post_dtos = []  # (entity_name, dto_cls)
    all_put_dtos = []
    
    for entity_name, need_post, need_put in config['entities']:
        # Find entity file
        entity_file = find_entity_file(mod_key, entity_name)
        if not entity_file:
            print(f"  WARN: Entity file not found for {entity_name}")
            continue
        
        fields = parse_entity_fields(entity_file)
        print(f"  {entity_name}: {len(fields)} business fields")
        
        # Create dto directories
        post_dir = os.path.join(domain_path, 'dto', 'post')
        put_dir = os.path.join(domain_path, 'dto', 'put')
        os.makedirs(post_dir, exist_ok=True)
        os.makedirs(put_dir, exist_ok=True)
        
        if need_post:
            content, pkg, dto_cls = generate_dto(entity_name, fields, mod_key, 'post')
            dto_path = os.path.join(post_dir, f'{dto_cls}.java')
            with open(dto_path, 'w', encoding='utf-8') as f:
                f.write(content)
            all_post_dtos.append((entity_name, dto_cls))
            print(f"    Created {dto_cls}")
        
        if need_put:
            content, pkg, dto_cls = generate_dto(entity_name, fields, mod_key, 'put')
            dto_path = os.path.join(put_dir, f'{dto_cls}.java')
            with open(dto_path, 'w', encoding='utf-8') as f:
                f.write(content)
            all_put_dtos.append((entity_name, dto_cls))
            print(f"    Created {dto_cls}")
    
    # Update Converter
    conv_name = config['converter_name']
    conv_path = os.path.join(domain_path, 'converter', f'{conv_name}.java')
    with open(conv_path, 'r', encoding='utf-8') as f:
        conv_content = f.read()
    
    # Add imports
    new_imports = []
    for entity_name, dto_cls in all_post_dtos:
        new_imports.append(f'import com.njydsz.{mod_key}.domain.dto.post.{dto_cls};')
    for entity_name, dto_cls in all_put_dtos:
        new_imports.append(f'import com.njydsz.{mod_key}.domain.dto.put.{dto_cls};')
    
    for imp in new_imports:
        if imp not in conv_content:
            last_imp = None
            for m in re.finditer(r'^import .+;$', conv_content, re.MULTILINE):
                last_imp = m
            if last_imp:
                insert_pos = last_imp.end()
                conv_content = conv_content[:insert_pos] + '\n' + imp + conv_content[insert_pos:]
    
    # Add @Mapping import if not present
    if 'import org.mapstruct.Mapping;' not in conv_content:
        conv_content = conv_content.replace('import org.mapstruct.factory.Mappers;',
            'import org.mapstruct.Mapping;\nimport org.mapstruct.factory.Mappers;')
    
    # Add converter methods before closing brace
    new_methods = []
    for entity_name, dto_cls in all_post_dtos:
        new_methods.append(f'    // ===== {entity_name} PostDTO → Entity =====')
        new_methods.append(IGNORE_FULL)
        new_methods.append(f'    {entity_name} postDtoToEntity({dto_cls} dto);')
        new_methods.append('')
    
    for entity_name, dto_cls in all_put_dtos:
        new_methods.append(f'    // ===== {entity_name} PutDTO → Entity =====')
        new_methods.append(IGNORE_NO_ID)
        new_methods.append(f'    {entity_name} putDtoToEntity({dto_cls} dto);')
        new_methods.append('')
    
    conv_content = conv_content.rstrip()
    if conv_content.endswith('}'):
        conv_content = conv_content[:-1] + '\n' + '\n'.join(new_methods) + '\n}'
    
    with open(conv_path, 'w', encoding='utf-8') as f:
        f.write(conv_content)
    print(f"  Updated {conv_name} with {len(all_post_dtos)} postDtoToEntity + {len(all_put_dtos)} putDtoToEntity")

print("\n=== DTO generation complete! ===")
print("Now manually update controllers...")
