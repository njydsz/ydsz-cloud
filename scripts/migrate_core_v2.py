#!/usr/bin/env python3
"""
Phase 2: Migrate core functionality to existing modules (no new modules).

- Feature Flag → DELETE (no business module uses it)
- Lifecycle → DELETE (no business module uses it)
- Metrics (AbstractModuleMetrics) → ydsz-common-base
- DAG (SpELConditionEvaluator, DagInstanceStatus, DagNodeStatus) → ydsz-common-domain
- Job (JobHandler, MapReduceProcessor, etc.) → ydsz-common-domain
"""
import shutil
import pathlib

BASE = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common')

# New modules to delete (created in phase 1, now reverting)
NEW_MODULES = [
    'ydsz-common-featureflag',
    'ydsz-common-lifecycle',
    'ydsz-common-metrics',
    'ydsz-common-dag',
    'ydsz-common-job',
]


def delete_new_modules():
    """Delete the 5 new module directories created in phase 1."""
    for mod in NEW_MODULES:
        mod_path = BASE / mod
        if mod_path.exists():
            shutil.rmtree(mod_path)
            print(f'DELETED: {mod}/')
        else:
            print(f'SKIP (not found): {mod}/')


if __name__ == '__main__':
    delete_new_modules()
    print('\nDone! New modules deleted.')
