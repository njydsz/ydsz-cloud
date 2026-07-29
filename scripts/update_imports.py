#!/usr/bin/env python3
"""
Update import statements across the backend codebase:
  com.njydsz.common.core.metrics     -> com.njydsz.common.metrics
  com.njydsz.common.core.job         -> com.njydsz.common.job
  com.njydsz.common.core.dag         -> com.njydsz.common.dag
  com.njydsz.common.core.featureflag -> com.njydsz.common.featureflag
  com.njydsz.common.core.lifecycle   -> com.njydsz.common.lifecycle

Also update string literals in GlueCodeServiceImpl.java that reference these packages.
"""
import pathlib
import re

BACKEND = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend')

REPLACEMENTS = [
    ('com.njydsz.common.core.metrics.', 'com.njydsz.common.metrics.'),
    ('com.njydsz.common.core.job.', 'com.njydsz.common.job.'),
    ('com.njydsz.common.core.dag.', 'com.njydsz.common.dag.'),
    ('com.njydsz.common.core.featureflag.', 'com.njydsz.common.featureflag.'),
    ('com.njydsz.common.core.lifecycle.', 'com.njydsz.common.lifecycle.'),
]

# Also update ydsz.core.graceful-shutdown -> ydsz.lifecycle.graceful-shutdown in YAML/properties
YAML_REPLACEMENTS = [
    ('ydsz.core.graceful-shutdown', 'ydsz.lifecycle.graceful-shutdown'),
]


def process_file(filepath):
    """Process a single file, return True if changed."""
    try:
        content = filepath.read_text(encoding='utf-8')
    except Exception:
        return False

    original = content
    for old, new in REPLACEMENTS:
        content = content.replace(old, new)

    # For YAML/properties files, also update config prefixes
    if filepath.suffix in ('.yml', '.yaml', '.properties'):
        for old, new in YAML_REPLACEMENTS:
            content = content.replace(old, new)

    if content != original:
        filepath.write_text(content, encoding='utf-8')
        return True
    return False


def main():
    changed_files = []
    # Process all .java, .yml, .yaml, .properties files
    extensions = {'.java', '.yml', '.yaml', '.properties'}
    for filepath in BACKEND.rglob('*'):
        if filepath.is_file() and filepath.suffix in extensions:
            # Skip target directories
            if 'target' in filepath.parts:
                continue
            if process_file(filepath):
                changed_files.append(str(filepath.relative_to(BACKEND)))

    print(f'Updated {len(changed_files)} files:')
    for f in changed_files:
        print(f'  {f}')


if __name__ == '__main__':
    main()
