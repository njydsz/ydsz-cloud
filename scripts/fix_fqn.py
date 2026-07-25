#!/usr/bin/env python3
"""Fix inline FQN violations in Java source files."""
import os
import re

# Files and their specific FQN fixes
FIXES = {
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-audit\src\main\java\com\njydsz\common\audit\health\AuditHealthIndicator.java': [
        ('java.util.Map<String, Object> details = new java.util.LinkedHashMap<>()', 'Map<String, Object> details = new LinkedHashMap<>()'),
        ('import java.util.Map;', 'import java.util.LinkedHashMap;\nimport java.util.Map;'),
    ],
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-feign\src\main\java\com\njydsz\common\feign\config\FeignConfiguration.java': [
        ('java.time.Duration.ofSeconds(ttlSeconds)', 'Duration.ofSeconds(ttlSeconds)'),
    ],
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-feign\src\main\java\com\njydsz\common\feign\aspect\YdszFeignLogger.java': [
        ('protected Response logAndRebufferResponse(String configKey, Logger.Level logLeve',
         None),  # Need to check actual content
    ],
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-feign\src\main\java\com\njydsz\common\feign\interceptor\BulkheadRequestInterceptor.java': [
        ('java.net.URI.create(url).getHost()', 'URI.create(url).getHost()'),
    ],
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-feign\src\main\java\com\njydsz\common\feign\monitor\FeignMicrometerCollector.java': [
        ('java.time.Duration.ofMillis(duration', 'Duration.ofMillis(duration'),
    ],
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-auth\src\main\java\com\njydsz\common\auth\security\TokenBlacklistBloomFilter.java': [
        ('private final java.util.BitSet bitSet;', 'private final BitSet bitSet;'),
        ('this.bitSet = new java.util.BitSet(bitArraySize);', 'this.bitSet = new BitSet(bitArraySize);'),
    ],
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-auth\src\main\java\com\njydsz\common\auth\service\impl\RedisRoleDataPermissionResolver.java': [
        ('new java.util.LinkedHashSet<>', 'new LinkedHashSet<>'),
    ],
}

for fpath, fixes in FIXES.items():
    if not os.path.exists(fpath):
        print(f'NOT FOUND: {fpath}')
        continue
    with open(fpath, 'r', encoding='utf-8') as f:
        content = f.read()
    original = content
    for old, new in fixes:
        if new is None:
            continue
        if old in content:
            content = content.replace(old, new)
            print(f'Fixed: {os.path.basename(fpath)}: {old[:50]}...')
        else:
            print(f'NOT FOUND in {os.path.basename(fpath)}: {old[:50]}...')

    if content != original:
        # Check if we need to add imports
        if 'BitSet' in content and 'import java.util.BitSet;' not in content:
            content = content.replace('import java.util.HexFormat;', 'import java.util.BitSet;\nimport java.util.HexFormat;')
            print(f'  Added import: BitSet')
        if 'LinkedHashSet' in content and 'import java.util.LinkedHashSet;' not in content and 'LinkedHashSet' in os.path.basename(fpath):
            pass  # Will handle separately
        if 'URI' in content and 'import java.net.URI;' not in content and 'Bulkhead' in os.path.basename(fpath):
            content = content.replace('import java.io.IOException;', 'import java.io.IOException;\nimport java.net.URI;')
            print(f'  Added import: URI')
        if 'Duration' in content and 'import java.time.Duration;' not in content:
            if 'FeignConfiguration' in fpath or 'FeignMicrometerCollector' in fpath:
                content = content.replace('import java.util.List;', 'import java.time.Duration;\nimport java.util.List;')
                print(f'  Added import: Duration')
        if 'LinkedHashMap' in content and 'import java.util.LinkedHashMap;' not in content and 'AuditHealth' in fpath:
            pass  # Already added in the fix
        with open(fpath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f'  Written: {fpath}')
    print()
