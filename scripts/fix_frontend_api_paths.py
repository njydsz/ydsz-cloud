#!/usr/bin/env python3
"""P0-1: 前端API路径标准化 — 将各子应用API文件中的路径统一为 /api/v1/{module}/... 格式"""

import os
import re

BASE = r"d:\Code\ydsz\ydsz-pmis\ydsz-frontend\apps"

# 前端路径替换规则
# 顺序很重要：先替换长路径，再替换短路径
REPLACEMENTS = [
    # workflow: /api/workflow/ → /api/v1/workflow/
    #            /workflow/ → /api/v1/workflow/
    (r'`/api/workflow/', r'`/api/v1/workflow/'),
    (r'`/workflow/', r'`/api/v1/workflow/'),

    # cronjob: /cronjob/ → /api/v1/cronjob/
    (r'`/cronjob/', r'`/api/v1/cronjob/'),
    (r'`/cronjob`', r'`/api/v1/cronjob`'),

    # literule: /ruleEngine/ → /api/v1/literule/
    (r'`/ruleEngine/', r'`/api/v1/literule/'),

    # agent: /agent/ → /api/v1/agent/
    (r'`/agent/', r'`/api/v1/agent/'),
    (r'`/agent`', r'`/api/v1/agent`'),

    # message: /message/ → /api/v1/message/
    #          /notifications → /api/v1/message/notifications
    #          /orchestration → /api/v1/message/orchestration
    #          /api/readReceipt → /api/v1/message/readReceipt
    #          /user-channels → /api/v1/message/user-channels
    #          /template/version → /api/v1/message/template/version
    #          /template/preview → /api/v1/message/template/preview
    #          /batch → /api/v1/message/batch
    #          /archive/search → /api/v1/message/archive/search
    (r'`/message/', r'`/api/v1/message/'),
    (r'`/message`', r'`/api/v1/message`'),
    (r'`/notifications', r'`/api/v1/message/notifications'),
    (r'`/orchestration', r'`/api/v1/message/orchestration'),
    (r'`/api/readReceipt', r'`/api/v1/message/readReceipt'),
    (r'`/user-channels', r'`/api/v1/message/user-channels'),
    (r'`/template/version', r'`/api/v1/message/template/version'),
    (r'`/template/preview', r'`/api/v1/message/template/preview'),
    (r'`/batch`', r'`/api/v1/message/batch`'),
    (r'`/archive/search', r'`/api/v1/message/archive/search'),
]


def find_ts_files(base_dir):
    """Find all .ts files under the base directory"""
    result = []
    for root, dirs, files in os.walk(base_dir):
        # Skip node_modules
        if 'node_modules' in root:
            continue
        for f in files:
            if f.endswith('.ts'):
                result.append(os.path.join(root, f))
    return result


def apply_replacements(file_path):
    """Apply path replacements to a single file"""
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    for old, new in REPLACEMENTS:
        content = re.sub(old, new, content)

    if content != original:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False


def main():
    ts_files = find_ts_files(BASE)
    modified = 0
    for ts_file in ts_files:
        if apply_replacements(ts_file):
            modified += 1
            print(f"MODIFIED: {ts_file}")
    print(f"\nTotal modified: {modified} files")


if __name__ == '__main__':
    main()
