#!/usr/bin/env python3
"""Remove VITE_NITRO_MOCK from all sub-app .env.development files."""
import os, glob

BASE = r"d:\Code\ydsz\ydsz-pmis\ydsz-frontend\apps"

for env_file in glob.glob(os.path.join(BASE, "*", ".env.development")):
    with open(env_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    new_lines = []
    for line in lines:
        if 'VITE_NITRO_MOCK' in line:
            continue
        new_lines.append(line)

    with open(env_file, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)

    print(f"  Cleaned: {os.path.relpath(env_file, BASE)}")

print("Done!")
