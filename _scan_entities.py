#!/usr/bin/env python3
"""Scan all entity files and generate VO + Converter for each module."""
import os
import re
import sys

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

def parse_entity_fields(filepath):
    """Parse private fields from a Java entity file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    fields = []
    # Skip serialVersionUID
    for m in re.finditer(r'private\s+([\w<>\[\].]+)\s+(\w+)\s*;', content):
        ftype = m.group(1)
        fname = m.group(2)
        if fname == 'serialVersionUID':
            continue
        fields.append((ftype, fname))
    return fields

def get_entity_class_name(filepath):
    """Extract class name from file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    m = re.search(r'class\s+(\w+)\s+extends', content)
    if m:
        return m.group(1)
    m = re.search(r'class\s+(\w+)', content)
    return m.group(1) if m else None

def get_entity_package(filepath):
    """Extract package from file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    m = re.search(r'package\s+([\w.]+);', content)
    return m.group(1) if m else None

def generate_vo(class_name, fields, package_base):
    """Generate VO class content."""
    vo_package = package_base.replace('.entity', '.vo').replace('.model', '.vo')
    # Handle nested entity packages like entity.batch, entity.core etc.
    parts = vo_package.rsplit('.', 1)
    if parts[0].endswith('.entity'):
        vo_package = parts[0].replace('.entity', '.vo')
    
    lines = []
    lines.append(f'package {vo_package};')
    lines.append('')
    # Collect imports
    imports = set()
    for ftype, _ in fields:
        if 'LocalDateTime' in ftype:
            imports.add('java.time.LocalDateTime')
        elif 'BigDecimal' in ftype:
            imports.add('java.math.BigDecimal')
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
    # Add id field first (from MpBaseEntity)
    lines.append('    private String id;')
    for ftype, fname in fields:
        if fname in ('deleted', 'revision', 'tenantId'):
            continue
        lines.append(f'    private {ftype} {fname};')
    # Add audit fields
    lines.append('    private String createdBy;')
    lines.append('    private LocalDateTime createdAt;')
    lines.append('    private String updatedBy;')
    lines.append('    private LocalDateTime updatedAt;')
    lines.append('}')
    return '\n'.join(lines)

def main():
    modules = {
        'cronjob': 'ydsz-cronjob',
        'workflow': 'ydsz-workflow',
        'project': 'ydsz-project',
    }
    
    for mod_key, mod_dir in modules.items():
        domain_path = os.path.join(BACKEND, mod_dir, f'ydsz-{mod_key}-domain', 'src', 'main', 'java')
        if not os.path.exists(domain_path):
            print(f'### {mod_key}: no domain dir at {domain_path}')
            continue
        
        # Find all entity files
        entity_files = []
        for root, dirs, files in os.walk(domain_path):
            for fn in files:
                if fn.endswith('.java') and '/entity/' in root.replace(os.sep, '/'):
                    entity_files.append(os.path.join(root, fn))
        
        print(f'### {mod_key}: {len(entity_files)} entities found')
        for ef in entity_files:
            cls = get_entity_class_name(ef)
            pkg = get_entity_package(ef)
            fields = parse_entity_fields(ef)
            print(f'  {cls} ({len(fields)} fields) pkg={pkg}')
            for ft, fn2 in fields:
                print(f'    {ft} {fn2}')

if __name__ == '__main__':
    main()
