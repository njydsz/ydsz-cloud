#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Print files needing comment work, grouped by module."""

import json
import sys

def main():
    with open('d:/Code/ydsz/ydsz-pmis/scripts/needs_work_files.json', 'r', encoding='utf-8') as f:
        data = json.load(f)

    target_modules = sys.argv[1:] if len(sys.argv) > 1 else None

    for r in data:
        if target_modules is None or r['module'] in target_modules:
            print(f'{r["module"]:20s} {r["priority"]:6s} {r["code_lines"]:5d}  {r["file"]}')

if __name__ == '__main__':
    main()
