#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Print files by reason category."""

import json
import sys

def main():
    with open('d:/Code/ydsz/ydsz-pmis/scripts/needs_work_v2.json', 'r', encoding='utf-8') as f:
        data = json.load(f)

    reason = sys.argv[1] if len(sys.argv) > 1 else 'no_class_javadoc'
    target_modules = sys.argv[2:] if len(sys.argv) > 2 else None

    for r in data:
        if r['reason'] == reason:
            if target_modules is None or r['module'] in target_modules:
                print(f'{r["module"]:20s} {r["code_lines"]:5d}  {r["class_name"]:30s}  {r["file"]}')

if __name__ == '__main__':
    main()
