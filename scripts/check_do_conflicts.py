#!/usr/bin/env python3
"""Check for naming conflicts before renaming DO entity classes."""
import pathlib
import re

backend = pathlib.Path(r'd:/Code/ydsz/ydsz-pmis/ydsz-backend')

# Collect all DO class names
do_files = list(backend.rglob('*DO.java'))
do_names = {f.stem for f in do_files}  # e.g. UserAccountDO

# Compute new names (without DO suffix)
new_names = {name[:-2] for name in do_names}  # e.g. UserAccount

# Check if any new name already exists as a .java file (that is NOT a DO file)
conflicts = []
do_file_paths = {f.resolve() for f in do_files}
for f in backend.rglob('*.java'):
    stem = f.stem
    if stem in new_names:
        if f.resolve() not in do_file_paths:
            conflicts.append(str(f))

if conflicts:
    print('CONFLICTS FOUND:')
    for c in sorted(conflicts):
        print(f'  {c}')
else:
    print('No naming conflicts found. Safe to proceed.')

print(f'Total DO files: {len(do_files)}')
print(f'Total new names: {len(new_names)}')

# Also check for DO references in YAML/properties files
yaml_refs = []
for ext in ['*.yml', '*.yaml', '*.properties']:
    for f in backend.rglob(ext):
        try:
            content = f.read_text(encoding='utf-8')
            for name in do_names:
                if name in content:
                    yaml_refs.append((str(f), name))
                    break
        except Exception:
            pass

if yaml_refs:
    print(f'\nYAML/properties files referencing DO names: {len(yaml_refs)}')
    for path, name in yaml_refs[:20]:
        print(f'  {path}: {name}')
else:
    print('\nNo YAML/properties files reference DO class names.')
