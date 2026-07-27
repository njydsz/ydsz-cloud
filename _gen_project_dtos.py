#!/usr/bin/env python3
"""
Generate PostDTO + PutDTO for all project entities that are used as @RequestBody in controllers.
Also update controllers to use PostDTO/PutDTO instead of Entity.
Also add postDtoToEntity + putDtoToEntity to ProjectConverter.
"""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'
PROJECT_DOMAIN = os.path.join(BACKEND, 'ydsz-project', 'ydsz-project-domain', 'src', 'main', 'java',
                               'com', 'njydsz', 'project', 'domain')
PROJECT_WEB = os.path.join(BACKEND, 'ydsz-project', 'ydsz-project-web', 'src', 'main', 'java',
                           'com', 'njydsz', 'project', 'web', 'controller')

def parse_entity_fields(filepath):
    """Parse private fields from entity, excluding MpBaseEntity fields."""
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

def get_entity_class_name(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    m = re.search(r'class\s+(\w+)\s+extends', content)
    if m:
        return m.group(1)
    m = re.search(r'class\s+(\w+)', content)
    return m.group(1) if m else None

def get_entity_package(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    m = re.search(r'package\s+([\w.]+);', content)
    return m.group(1) if m else None

# Step 1: Scan all controllers to find which entities are used as @RequestBody
print("=== Step 1: Scanning controllers for @RequestBody Entity ===")
entity_usage = {}  # entity_name -> {controller_file, methods}

for fn in os.listdir(PROJECT_WEB):
    if not fn.endswith('Controller.java'):
        continue
    fp = os.path.join(PROJECT_WEB, fn)
    with open(fp, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Find @RequestBody Entity patterns
    for m in re.finditer(r'@RequestBody\s+(\w+)\s+\w+', content):
        param_type = m.group(1)
        # Skip DTOs, VOs, queries, primitives
        if any(x in param_type for x in ['DTO', 'VO', 'Query', 'Request', 'String', 'Boolean', 'Integer', 'Long', 'Map', 'List', 'Object']):
            continue
        if param_type not in entity_usage:
            entity_usage[param_type] = []
        entity_usage[param_type].append(fn)

print(f"Found {len(entity_usage)} entities used as @RequestBody:")
for entity, controllers in sorted(entity_usage.items()):
    print(f"  {entity}: {controllers}")

# Step 2: Find entity files and parse fields
print("\n=== Step 2: Parsing entity fields ===")
entity_info = {}  # entity_name -> (fields, package, entity_file)

for entity_name in entity_usage:
    # Find the entity file
    entity_found = False
    for root, dirs, files in os.walk(os.path.join(PROJECT_DOMAIN, 'entity')):
        for fn in files:
            if fn == entity_name + '.java':
                fp = os.path.join(root, fn)
                fields = parse_entity_fields(fp)
                pkg = get_entity_package(fp)
                entity_info[entity_name] = (fields, pkg, fp)
                print(f"  {entity_name}: {len(fields)} business fields, pkg={pkg}")
                entity_found = True
                break
        if entity_found:
            break
    if not entity_found:
        print(f"  {entity_name}: NOT FOUND!")

# Step 3: Create dto/post/ and dto/put/ directories and generate DTOs
print("\n=== Step 3: Generating PostDTO + PutDTO ===")
dto_post_dir = os.path.join(PROJECT_DOMAIN, 'dto', 'post')
dto_put_dir = os.path.join(PROJECT_DOMAIN, 'dto', 'put')
os.makedirs(dto_post_dir, exist_ok=True)
os.makedirs(dto_put_dir, exist_ok=True)

post_dtos = []  # (entity_name, dto_class_name)
put_dtos = []

for entity_name, (fields, pkg, _) in entity_info.items():
    # Generate PostDTO (without id)
    post_cls = entity_name + 'PostDTO'
    put_cls = entity_name + 'PutDTO'
    
    # Collect imports
    imports = set()
    for ftype, _ in fields:
        if 'LocalDateTime' in ftype:
            imports.add('java.time.LocalDateTime')
        elif 'LocalDate' in ftype:
            imports.add('java.time.LocalDate')
        elif 'BigDecimal' in ftype:
            imports.add('java.math.BigDecimal')
    
    # PostDTO
    lines = [
        f'package com.njydsz.project.domain.dto.post;',
        '',
    ]
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
        f' * {entity_name} 新增请求 DTO。',
        ' *',
        ' * @author ydsz-team',
        ' * @since 1.0.0',
        ' */',
        '@Data',
        f'public class {post_cls} implements Serializable {{',
        '',
        '    @Serial',
        '    private static final long serialVersionUID = 1L;',
        '',
    ])
    for ftype, fname in fields:
        lines.append(f'    private {ftype} {fname};')
    lines.append('}')
    
    post_path = os.path.join(dto_post_dir, f'{post_cls}.java')
    with open(post_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))
    post_dtos.append((entity_name, post_cls))
    
    # PutDTO (with id)
    lines = [
        f'package com.njydsz.project.domain.dto.put;',
        '',
    ]
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
        f' * {entity_name} 修改请求 DTO。',
        ' *',
        ' * @author ydsz-team',
        ' * @since 1.0.0',
        ' */',
        '@Data',
        f'public class {put_cls} implements Serializable {{',
        '',
        '    @Serial',
        '    private static final long serialVersionUID = 1L;',
        '',
        '    private String id;',
    ])
    for ftype, fname in fields:
        lines.append(f'    private {ftype} {fname};')
    lines.append('}')
    
    put_path = os.path.join(dto_put_dir, f'{put_cls}.java')
    with open(put_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))
    put_dtos.append((entity_name, put_cls))
    
    print(f"  Created {post_cls} + {put_cls} ({len(fields)} fields)")

# Step 4: Update ProjectConverter with postDtoToEntity + putDtoToEntity
print("\n=== Step 4: Updating ProjectConverter ===")
conv_path = os.path.join(PROJECT_DOMAIN, 'converter', 'ProjectConverter.java')
with open(conv_path, 'r', encoding='utf-8') as f:
    conv_content = f.read()

# Add imports for new DTOs
new_imports = []
for entity_name, post_cls in post_dtos:
    new_imports.append(f'import com.njydsz.project.domain.dto.post.{post_cls};')
for entity_name, put_cls in put_dtos:
    new_imports.append(f'import com.njydsz.project.domain.dto.put.{put_cls};')

# Find last import and add after it
last_import_match = None
for m in re.finditer(r'^import .+;$', conv_content, re.MULTILINE):
    last_import_match = m

if last_import_match:
    insert_pos = last_import_match.end()
    conv_content = conv_content[:insert_pos] + '\n' + '\n'.join(new_imports) + conv_content[insert_pos:]

# Add converter methods before the closing brace
ignore_annotations = '''    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)'''

ignore_no_id = '''    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)'''

new_methods = []
for entity_name, post_cls in post_dtos:
    new_methods.append(f'    // ===== {entity_name} PostDTO → Entity =====')
    new_methods.append(ignore_annotations)
    new_methods.append(f'    {entity_name} postDtoToEntity({post_cls} dto);')
    new_methods.append('')

for entity_name, put_cls in put_dtos:
    new_methods.append(f'    // ===== {entity_name} PutDTO → Entity =====')
    new_methods.append(ignore_no_id)
    new_methods.append(f'    {entity_name} putDtoToEntity({put_cls} dto);')
    new_methods.append('')

# Insert before closing brace
conv_content = conv_content.rstrip()
if conv_content.endswith('}'):
    conv_content = conv_content[:-1] + '\n' + '\n'.join(new_methods) + '\n}'

# Add @Mapping import if not present
if 'import org.mapstruct.Mapping;' not in conv_content:
    conv_content = conv_content.replace('import org.mapstruct.factory.Mappers;',
        'import org.mapstruct.Mapping;\nimport org.mapstruct.factory.Mappers;')

with open(conv_path, 'w', encoding='utf-8') as f:
    f.write(conv_content)
print(f"  Updated ProjectConverter with {len(post_dtos)} postDtoToEntity + {len(put_dtos)} putDtoToEntity methods")

# Step 5: Update controllers to use PostDTO/PutDTO
print("\n=== Step 5: Updating controllers ===")

# Build entity -> post_cls / put_cls mapping
entity_to_post = {e: p for e, p in post_dtos}
entity_to_put = {e: p for e, p in put_dtos}

for fn in os.listdir(PROJECT_WEB):
    if not fn.endswith('Controller.java'):
        continue
    fp = os.path.join(PROJECT_WEB, fn)
    with open(fp, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    imports_to_add = set()
    
    for entity_name in entity_usage:
        if entity_name not in entity_to_post:
            continue
        post_cls = entity_to_post[entity_name]
        put_cls = entity_to_put[entity_name]
        
        # Check if this controller uses this entity
        if f'@RequestBody {entity_name} ' not in content:
            continue
        
        # Replace POST: @RequestBody Entity e -> @RequestBody PostDTO dto
        # Pattern: @RequestBody EntityName varName)
        post_pattern = rf'(@PostMapping[^\n]*\n.*?@RequestBody\s+){entity_name}(\s+\w+\))'
        content = re.sub(
            rf'(@RequestBody\s+){entity_name}(\s+\w+\))',
            lambda m, pc=post_cls: m.group(1) + pc + ' dto)',
            content,
            count=1  # Only replace first (POST)
        )
        
        # Replace PUT: @RequestBody Entity e -> @RequestBody PutDTO dto
        remaining = content.count(f'@RequestBody {entity_name} ')
        if remaining > 0:
            content = re.sub(
                rf'(@RequestBody\s+){entity_name}(\s+\w+\))',
                lambda m, pc=put_cls: m.group(1) + pc + ' dto)',
                content,
                count=1  # Only replace next (PUT)
            )
        
        # Replace service calls: service.save(e) -> service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))
        # And: service.updateById(e) -> service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))
        # Pattern: service.save(e) -> service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))
        content = re.sub(
            rf'service\.save\(\s*(\w+)\s*\)',
            lambda m: f'service.save(ProjectConverter.INSTANT.postDtoToEntity({m.group(1)}))',
            content
        )
        content = re.sub(
            rf'service\.updateById\(\s*(\w+)\s*\)',
            lambda m: f'service.updateById(ProjectConverter.INSTANT.putDtoToEntity({m.group(1)}))',
            content
        )
        
        # Add imports
        imports_to_add.add(f'import com.njydsz.project.domain.dto.post.{post_cls};')
        imports_to_add.add(f'import com.njydsz.project.domain.dto.put.{put_cls};')
    
    if content != original:
        # Add imports
        for imp in imports_to_add:
            if imp not in content:
                # Find last import
                last_imp = None
                for m in re.finditer(r'^import .+;$', content, re.MULTILINE):
                    last_imp = m
                if last_imp:
                    insert_pos = last_imp.end()
                    content = content[:insert_pos] + '\n' + imp + content[insert_pos:]
        
        with open(fp, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"  Updated: {fn}")

print("\n=== Done! ===")
print(f"Created {len(post_dtos)} PostDTO + {len(put_dtos)} PutDTO files")
