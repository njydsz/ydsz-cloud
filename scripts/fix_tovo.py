#!/usr/bin/env python3
"""P2-1: Replace this::toVO with UserInfoConverter.INSTANT::entityToVO in userinfo ServiceImpl files,
then delete the toVO method definitions."""

import os
import re

BASE = r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-userinfo\ydsz-userinfo-server\src\main\java\com\njydsz\userinfo\server\service\impl"

FILES = [
    "LanguageServiceImpl.java",
    "RoleServiceImpl.java",
    "UserAccountServiceImpl.java",
    "PostServiceImpl.java",
    "MenuServiceImpl.java",
    "DepartmentServiceImpl.java",
]


def process_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content

    # Replace this::toVO with UserInfoConverter.INSTANT::entityToVO
    content = content.replace('this::toVO', 'UserInfoConverter.INSTANT::entityToVO')

    # Delete toVO method definitions (various return types)
    # Pattern: private XxxVO toVO(Xxx entity) { ... }
    # Match from 'private XxxVO toVO(' to the closing brace
    content = re.sub(
        r'\s*private\s+\w+VO\s+toVO\([^)]+\)\s*\{[^}]*\}\s*',
        '\n',
        content,
        flags=re.DOTALL
    )

    # Also handle: protected XxxVO toVO(...) { ... }
    content = re.sub(
        r'\s*protected\s+\w+VO\s+toVO\([^)]+\)\s*\{[^}]*\}\s*',
        '\n',
        content,
        flags=re.DOTALL
    )

    # Clean up: remove unused imports if UserInfoConverter was already imported
    # (it should already be imported in these files)

    if content != original:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False


def main():
    modified = 0
    for fname in FILES:
        fpath = os.path.join(BASE, fname)
        if not os.path.exists(fpath):
            print(f"NOT FOUND: {fpath}")
            continue
        if process_file(fpath):
            modified += 1
            print(f"MODIFIED: {fpath}")
        else:
            print(f"NO CHANGES: {fpath}")
    print(f"\nTotal modified: {modified} files")


if __name__ == '__main__':
    main()
