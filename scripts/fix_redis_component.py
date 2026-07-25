#!/usr/bin/env python3
"""Batch remove @Component/@Service from Redis module classes."""
import os
import re

BASE = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-redis\src\main\java'

TARGETS = [
    'RedisBloomFilter.java',
    'RedisDelayedQueue.java',
    'RedisRateLimiter.java',
    'RedisService.java',
    'RedisSnowflakeIdGenerator.java',
    'RedisAdvancedOps.java',
    'RedisCollectionOps.java',
    'RedisGeoOps.java',
    'RedisHashOps.java',
    'RedisPubSubOps.java',
    'RedisStreamOps.java',
    'RedisStringOps.java',
    'RedisTransactionOps.java',
    'RedisConnectionFactoryConfigurer.java',
]

for root, dirs, files in os.walk(BASE):
    for fn in files:
        if fn not in TARGETS:
            continue
        fpath = os.path.join(root, fn)
        with open(fpath, 'r', encoding='utf-8') as f:
            content = f.read()

        original = content

        # Remove @Component or @Service annotation (on its own line)
        content = re.sub(r'\n@Component\b', '', content)
        content = re.sub(r'\n@Service\b', '', content)

        # Remove the import statements
        content = re.sub(r'import org\.springframework\.stereotype\.Component;\n', '', content)
        content = re.sub(r'import org\.springframework\.stereotype\.Service;\n', '', content)

        if content != original:
            with open(fpath, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f'Fixed: {fn}')
        else:
            print(f'Skipped (no change): {fn}')

print('Done!')
