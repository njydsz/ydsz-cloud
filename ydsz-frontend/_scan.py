#!/usr/bin/env python3
"""Scan all remaining sub-app controllers."""
import os, re, glob

BASE = r"d:\Code\ydsz\ydsz-pmis\ydsz-backend"

apps = {
    'project-web': 'ydsz-project',
    'message-web': 'ydsz-message',
    'cronjob-web': 'ydsz-cronjob',
    'workflow-web': 'ydsz-workflow',
    'nextwiki-web': 'ydsz-nextwiki',
    'literule-web': 'ydsz-literule',
    'agent-web': 'ydsz-agent',
}

for app_name, backend_module in apps.items():
    web_dir = os.path.join(BASE, backend_module, f'{backend_module}-web')
    controllers = glob.glob(os.path.join(web_dir, '**', '*Controller.java'), recursive=True)
    print(f'\n=== {app_name} ({len(controllers)} controllers) ===')
    for ctrl in sorted(controllers):
        fname = os.path.basename(ctrl).replace('Controller.java', '')
        with open(ctrl, 'r', encoding='utf-8') as f:
            content = f.read()
        rm = re.search(r'@RequestMapping\("([^"]+)"\)', content)
        path = rm.group(1) if rm else '/api/v1/unknown'
        tag = re.search(r'@Tag\(\s*name\s*=\s*"([^"]+)"', content)
        tag_name = tag.group(1) if tag else fname
        print(f'  {fname}: {path} -> {tag_name}')
