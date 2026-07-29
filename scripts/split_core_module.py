#!/usr/bin/env python3
"""
Split ydsz-common-core module: extract featureflag/lifecycle/metrics/dag/job
into independent Maven modules with updated package names.
"""
import os
import shutil
import pathlib

BASE = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common')
CORE_SRC = BASE / 'ydsz-common-core' / 'src' / 'main' / 'java' / 'com' / 'njydsz' / 'common' / 'core'

# (old_package_suffix, new_module_name, new_package)
MODULES = [
    ('featureflag', 'ydsz-common-featureflag', 'com.njydsz.common.featureflag'),
    ('lifecycle',   'ydsz-common-lifecycle',   'com.njydsz.common.lifecycle'),
    ('metrics',     'ydsz-common-metrics',     'com.njydsz.common.metrics'),
    ('dag',         'ydsz-common-dag',         'com.njydsz.common.dag'),
    ('job',         'ydsz-common-job',         'com.njydsz.common.job'),
]


def move_files():
    """Move Java files from core to new modules with package updates."""
    for old_suffix, new_module, new_pkg in MODULES:
        old_dir = CORE_SRC / old_suffix
        if not old_dir.exists():
            print(f'SKIP (not found): {old_suffix}')
            continue

        # Create new module directory structure
        pkg_parts = new_pkg.split('.')
        new_src_dir = BASE / new_module / 'src' / 'main' / 'java' / pathlib.Path(*pkg_parts)
        new_res_dir = BASE / new_module / 'src' / 'main' / 'resources' / 'META-INF' / 'spring'
        new_src_dir.mkdir(parents=True, exist_ok=True)
        new_res_dir.mkdir(parents=True, exist_ok=True)

        # Move and update package declarations
        old_pkg = f'com.njydsz.common.core.{old_suffix}'
        for java_file in old_dir.glob('*.java'):
            content = java_file.read_text(encoding='utf-8')
            # Update package declaration
            content = content.replace(f'package {old_pkg};', f'package {new_pkg};')
            # Update internal imports within the same package
            content = content.replace(f'import {old_pkg}.', f'import {new_pkg}.')

            new_file = new_src_dir / java_file.name
            new_file.write_text(content, encoding='utf-8')
            print(f'MOVED: {old_suffix}/{java_file.name} -> {new_module}')

        # Delete old directory
        shutil.rmtree(old_dir)
        print(f'DELETED old dir: core/{old_suffix}')


if __name__ == '__main__':
    move_files()
    print('\nDone! Files moved successfully.')
