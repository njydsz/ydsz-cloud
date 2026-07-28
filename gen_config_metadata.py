#!/usr/bin/env python3
"""
Auto-generate additional-spring-configuration-metadata.json for common modules.
Scans @ConfigurationProperties classes and extracts prefix + fields.
"""
import json
import os
import re

BASE = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common'

# Modules needing metadata
MODULES = [
    'ydsz-common-audit', 'ydsz-common-auth', 'ydsz-common-cache',
    'ydsz-common-config', 'ydsz-common-domain', 'ydsz-common-feign',
    'ydsz-common-jdbc', 'ydsz-common-json', 'ydsz-common-lock',
    'ydsz-common-notify', 'ydsz-common-redis', 'ydsz-common-util'
]

def extract_properties_info(java_file):
    """Extract @ConfigurationProperties prefix and field info from a Java file."""
    with open(java_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Extract prefix
    prefix_match = re.search(r'@ConfigurationProperties\s*\(\s*(?:prefix\s*=\s*)?["\']([^"\']+)["\']', content)
    if not prefix_match:
        return None
    prefix = prefix_match.group(1)
    
    # Simple extraction: find private fields with their Javadoc comments
    # This is a simplified approach - just extracts field names
    fields = []
    
    # Pattern: javadoc + field declaration
    # Look for private/non-static fields
    field_pattern = re.compile(
        r'(?:/\*\*\s*\n((?:\s*\*\s*[^\n]*\n)*)\s*\*/)?\s*'
        r'(?:@\w+(?:\([^)]*\))?\s*)*'
        r'\s*(?:private|protected)\s+(?:final\s+)?(\w+(?:<[^>]+>)?)\s+(\w+)\s*[=;]',
        re.MULTILINE
    )
    
    for match in field_pattern.finditer(content):
        javadoc_block = match.group(1) or ''
        field_type = match.group(2)
        field_name = match.group(3)
        
        # Skip static/serialVersionUID
        if 'serialVersionUID' in field_name:
            continue
            
        # Extract first line of javadoc as description
        desc_lines = []
        for line in javadoc_block.split('\n'):
            line = line.strip().lstrip('*').strip()
            if line and not line.startswith('@'):
                desc_lines.append(line)
        description = ' '.join(desc_lines)[:200] if desc_lines else f'{field_name} configuration'
        
        # Convert camelCase to kebab-case
        kebab_name = re.sub(r'([a-z])([A-Z])', r'\1-\2', field_name).lower()
        
        # Determine type
        if field_type in ('boolean', 'Boolean'):
            prop_type = 'java.lang.Boolean'
        elif field_type in ('int', 'Integer'):
            prop_type = 'java.lang.Integer'
        elif field_type in ('long', 'Long'):
            prop_type = 'java.lang.Long'
        elif field_type in ('double', 'Double'):
            prop_type = 'java.lang.Double'
        elif field_type.startswith('String'):
            prop_type = 'java.lang.String'
        elif 'List' in field_type or field_type.endswith('[]'):
            prop_type = 'java.util.List'
        else:
            prop_type = 'java.lang.Object'
        
        fields.append({
            'name': f'{prefix}.{kebab_name}',
            'type': prop_type,
            'description': description
        })
    
    return prefix, fields


def generate_metadata_for_module(module_name):
    """Generate metadata JSON for a module."""
    src_dir = os.path.join(BASE, module_name, 'src', 'main', 'java')
    if not os.path.exists(src_dir):
        return None
    
    all_properties = []
    
    for root, dirs, files in os.walk(src_dir):
        for f in files:
            if f.endswith('.java'):
                java_file = os.path.join(root, f)
                result = extract_properties_info(java_file)
                if result:
                    prefix, fields = result
                    all_properties.extend(fields)
    
    if not all_properties:
        return None
    
    metadata = {
        'properties': all_properties
    }
    
    return metadata


def main():
    for module in MODULES:
        metadata = generate_metadata_for_module(module)
        if metadata and metadata.get('properties'):
            meta_dir = os.path.join(BASE, module, 'src', 'main', 'resources', 'META-INF')
            os.makedirs(meta_dir, exist_ok=True)
            output_file = os.path.join(meta_dir, 'additional-spring-configuration-metadata.json')
            
            with open(output_file, 'w', encoding='utf-8') as f:
                json.dump(metadata, f, indent=2, ensure_ascii=False)
            
            print(f'✅ {module}: {len(metadata["properties"])} properties → {output_file}')
        else:
            print(f'⚠️  {module}: no @ConfigurationProperties found or no fields extracted')

if __name__ == '__main__':
    main()
