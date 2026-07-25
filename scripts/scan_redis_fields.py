#!/usr/bin/env python3
"""Extract constructor fields from Redis module @RequiredArgsConstructor classes."""
import os
import re

BASE = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-redis\src\main\java'

TARGETS = [
    'RedisCollectionOps.java',
    'RedisGeoOps.java',
    'RedisHashOps.java',
    'RedisPubSubOps.java',
    'RedisStringOps.java',
]

for root, dirs, files in os.walk(BASE):
    for fn in files:
        if fn not in TARGETS:
            continue
        fpath = os.path.join(root, fn)
        with open(fpath, 'r', encoding='utf-8') as f:
            content = f.read()

        # Find private final fields
        fields = re.findall(r'private\s+final\s+(\w+(?:<[^>]+>)?)\s+(\w+)', content)
        print(f'{fn}:')
        for ftype, fname in fields:
            print(f'  {ftype} {fname}')
        print()
