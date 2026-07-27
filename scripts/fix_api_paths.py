#!/usr/bin/env python3
"""P0-1: API路径全量标准化 — 将所有Controller的@RequestMapping路径统一为 /api/v1/{module}/... 格式"""

import os
import re

BASE = r"d:\Code\ydsz\ydsz-pmis\ydsz-backend"

# 路径替换规则: (文件目录, 旧路径前缀模式, 新路径前缀)
# 用正则匹配 @RequestMapping("xxx") 并替换
REPLACEMENTS = {
    # === workflow ===
    # /workflow/xxx → /api/v1/workflow/xxx
    # /api/workflow/xxx → /api/v1/workflow/xxx
    r'ydsz-workflow.*controller': [
        (r'@RequestMapping\("/workflow/', r'@RequestMapping("/api/v1/workflow/'),
        (r'@RequestMapping\("/api/workflow/', r'@RequestMapping("/api/v1/workflow/'),
    ],
    # === cronjob ===
    r'ydsz-cronjob.*controller': [
        (r'@RequestMapping\("/cronjob', r'@RequestMapping("/api/v1/cronjob'),
    ],
    # === literule ===
    r'ydsz-literule.*controller': [
        (r'@RequestMapping\("/ruleEngine/', r'@RequestMapping("/api/v1/literule/'),
    ],
    # === agent ===
    r'ydsz-agent.*controller': [
        (r'@RequestMapping\("/agent', r'@RequestMapping("/api/v1/agent'),
    ],
    # === message === (mixed patterns)
    r'ydsz-message.*controller': [
        # /message/xxx → /api/v1/message/xxx
        (r'@RequestMapping\("/message/', r'@RequestMapping("/api/v1/message/'),
        # /message" (exact, no trailing slash) → /api/v1/message"
        (r'@RequestMapping\("/message"\)', r'@RequestMapping("/api/v1/message")'),
        # /notifications → /api/v1/message/notifications
        (r'@RequestMapping\("/notifications"', r'@RequestMapping("/api/v1/message/notifications"'),
        # /orchestration → /api/v1/message/orchestration
        (r'@RequestMapping\("/orchestration"', r'@RequestMapping("/api/v1/message/orchestration"'),
        # /api/readReceipt → /api/v1/message/readReceipt
        (r'@RequestMapping\("/api/readReceipt"', r'@RequestMapping("/api/v1/message/readReceipt"'),
        # /user-channels → /api/v1/message/user-channels
        (r'@RequestMapping\("/user-channels"', r'@RequestMapping("/api/v1/message/user-channels"'),
        # /template/version → /api/v1/message/template/version
        (r'@RequestMapping\("/template/version"', r'@RequestMapping("/api/v1/message/template/version"'),
        # /template/preview → /api/v1/message/template/preview
        (r'@RequestMapping\("/template/preview"', r'@RequestMapping("/api/v1/message/template/preview"'),
        # /batch → /api/v1/message/batch
        (r'@RequestMapping\("/batch"', r'@RequestMapping("/api/v1/message/batch"'),
        # /archive/search → /api/v1/message/archive/search
        (r'@RequestMapping\("/archive/search"', r'@RequestMapping("/api/v1/message/archive/search"'),
    ],
}


def find_controller_files(base_dir):
    """Find all Controller.java files under the base directory"""
    result = []
    for root, dirs, files in os.walk(base_dir):
        for f in files:
            if f.endswith("Controller.java"):
                result.append(os.path.join(root, f))
    return result


def apply_replacements(file_path):
    """Apply path replacements to a single file"""
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    for pattern_re, replacements in REPLACEMENTS.items():
        # Check if file path matches the pattern
        normalized = file_path.replace('\\', '/')
        if re.search(pattern_re, normalized):
            for old, new in replacements:
                content = re.sub(old, new, content)

    if content != original:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False


def main():
    controllers = find_controller_files(BASE)
    modified = 0
    for ctrl in controllers:
        if apply_replacements(ctrl):
            modified += 1
            print(f"MODIFIED: {ctrl}")
    print(f"\nTotal modified: {modified} files")


if __name__ == '__main__':
    main()
