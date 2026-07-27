#!/usr/bin/env python3
"""
Generate VO + Converter files for all remaining modules.
Creates a unified Converter per module with entityToVO + listToVO methods.
"""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

def parse_entity_fields(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    fields = []
    for m in re.finditer(r'private\s+([\w<>\[\].]+)\s+(\w+)\s*;', content):
        ftype = m.group(1)
        fname = m.group(2)
        if fname == 'serialVersionUID':
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

def generate_vo(class_name, fields, entity_pkg):
    vo_pkg = entity_pkg.replace('.entity.', '.vo.')
    # Also handle .entity at end
    vo_pkg = vo_pkg.replace('.entity', '.vo')
    # Remove sub-packages like .batch, .core etc for flat vo package
    vo_pkg = re.sub(r'\.vo\.\w+$', '.vo', vo_pkg)
    
    imports = set()
    for ftype, _ in fields:
        if 'LocalDateTime' in ftype:
            imports.add('java.time.LocalDateTime')
        elif 'BigDecimal' in ftype:
            imports.add('java.math.BigDecimal')
        elif 'LocalDate' in ftype and 'LocalDateTime' not in ftype:
            imports.add('java.time.LocalDate')
    
    lines = [f'package {vo_pkg};', '']
    if imports:
        for imp in sorted(imports):
            lines.append(f'import {imp};')
        lines.append('')
    lines.append('import java.io.Serial;')
    lines.append('import java.io.Serializable;')
    lines.append('import lombok.Data;')
    lines.append('')
    lines.append('/**')
    lines.append(f' * {class_name} 视图对象。')
    lines.append(' *')
    lines.append(' * @author ydsz-team')
    lines.append(' * @since 1.0.0')
    lines.append(' */')
    lines.append('@Data')
    lines.append(f'public class {class_name}VO implements Serializable {{')
    lines.append('')
    lines.append('    @Serial')
    lines.append('    private static final long serialVersionUID = 1L;')
    lines.append('')
    lines.append('    private String id;')
    for ftype, fname in fields:
        if fname in ('deleted', 'revision', 'tenantId'):
            continue
        lines.append(f'    private {ftype} {fname};')
    lines.append('    private String createdBy;')
    if 'LocalDateTime' in str(imports) or True:
        lines.append('    private LocalDateTime createdAt;')
    lines.append('    private String updatedBy;')
    lines.append('    private LocalDateTime updatedAt;')
    lines.append('}')
    return '\n'.join(lines), vo_pkg

def generate_converter(module_name, entities_info):
    """Generate a unified Converter interface for a module."""
    pkg = f'com.njydsz.{module_name}.domain.converter'
    
    lines = [f'package {pkg};', '', 'import java.util.List;', '', 'import org.mapstruct.Mapper;']
    lines.append('import org.mapstruct.factory.Mappers;')
    lines.append('')
    
    # Collect imports
    entity_imports = []
    vo_imports = []
    for cls, entity_pkg, vo_pkg in entities_info:
        entity_imports.append(f'{entity_pkg}.{cls}')
        vo_imports.append(f'{vo_pkg}.{cls}VO')
    
    for ei in entity_imports:
        lines.append(f'import {ei};')
    for vi in vo_imports:
        lines.append(f'import {vi};')
    lines.append('')
    
    conv_name = module_name.capitalize() + 'Converter'
    lines.append('/**')
    lines.append(f' * {module_name} 模块统一 MapStruct 转换器。')
    lines.append(' *')
    lines.append(' * @author ydsz-team')
    lines.append(' * @since 1.0.0')
    lines.append(' */')
    lines.append('@Mapper')
    lines.append(f'public interface {conv_name} {{')
    lines.append('')
    lines.append(f'    {conv_name} INSTANT = Mappers.getMapper({conv_name}.class);')
    lines.append('')
    
    for cls, _, _ in entities_info:
        vo = cls + 'VO'
        # Convert first char to lowercase for list method name
        list_method = cls[0].lower() + cls[1:] + 'ListToVO'
        lines.append(f'    // ===== {cls} =====')
        lines.append(f'    {vo} entityToVO({cls} entity);')
        lines.append(f'    List<{vo}> {list_method}(List<{cls}> entities);')
        lines.append('')
    
    lines.append('}')
    return '\n'.join(lines), pkg

def process_module(module_key, module_dir):
    domain_java = os.path.join(BACKEND, module_dir, f'ydsz-{module_key}-domain', 'src', 'main', 'java')
    if not os.path.exists(domain_java):
        print(f'  {module_key}: domain dir not found at {domain_java}')
        return
    
    # Find entity files - check both entity/ dir and entity subdirs
    entity_files = []
    for root, dirs, files in os.walk(domain_java):
        path_str = root.replace(os.sep, '/')
        if '/entity/' in path_str or path_str.endswith('/entity'):
            for fn in files:
                if fn.endswith('.java'):
                    entity_files.append(os.path.join(root, fn))
    
    if not entity_files:
        print(f'  {module_key}: no entity files found')
        return
    
    entities_info = []
    vo_dir_created = False
    for ef in entity_files:
        cls = get_entity_class_name(ef)
        pkg = get_entity_package(ef)
        fields = parse_entity_fields(ef)
        if not cls or not pkg:
            continue
        
        vo_content, vo_pkg = generate_vo(cls, fields, pkg)
        vo_dir = os.path.join(domain_java, vo_pkg.replace('.', os.sep))
        os.makedirs(vo_dir, exist_ok=True)
        vo_path = os.path.join(vo_dir, f'{cls}VO.java')
        with open(vo_path, 'w', encoding='utf-8') as f:
            f.write(vo_content)
        entities_info.append((cls, pkg, vo_pkg))
        print(f'  Created {cls}VO ({len(fields)} fields)')
    
    # Generate converter
    if entities_info:
        conv_content, conv_pkg = generate_converter(module_key, entities_info)
        conv_dir = os.path.join(domain_java, conv_pkg.replace('.', os.sep))
        os.makedirs(conv_dir, exist_ok=True)
        conv_name = module_key.capitalize() + 'Converter'
        conv_path = os.path.join(conv_dir, f'{conv_name}.java')
        with open(conv_path, 'w', encoding='utf-8') as f:
            f.write(conv_content)
        print(f'  Created {conv_name} with {len(entities_info)} entity mappings')
    
    # Update POM
    pom_path = os.path.join(BACKEND, module_dir, f'ydsz-{module_key}-domain', 'pom.xml')
    if os.path.exists(pom_path):
        with open(pom_path, 'r', encoding='utf-8') as f:
            pom = f.read()
        if 'mapstruct' not in pom:
            pom = pom.replace('</dependencies>', 
                '        <!-- MapStruct -->\n        <dependency>\n            <groupId>org.mapstruct</groupId>\n            <artifactId>mapstruct</artifactId>\n            <scope>provided</scope>\n        </dependency>\n    </dependencies>')
            with open(pom_path, 'w', encoding='utf-8') as f:
                f.write(pom)
            print(f'  Updated POM with mapstruct dependency')

print('Processing modules...')
for mk, md in [('cronjob', 'ydsz-cronjob'), ('workflow', 'ydsz-workflow'), ('project', 'ydsz-project'),
                ('agent', 'ydsz-agent'), ('literule', 'ydsz-literule')]:
    print(f'\n=== {mk} ===')
    process_module(mk, md)

print('\nDone!')
