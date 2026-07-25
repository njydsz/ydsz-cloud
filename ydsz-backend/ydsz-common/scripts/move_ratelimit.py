import pathlib

src = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-ratelimit\src\main\java\com\njydsz\common\ratelimit')
dst = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-safe\src\main\java\com\njydsz\common\safe\ratelimit')

mapping = {
    'algorithm': 'algorithm',
    'circuitbreaker': 'circuitbreaker',
    'cluster': 'cluster',
    'core': 'core',
    'enums': 'enums',
    'metrics': 'metrics',
    'model': 'model',
    'properties': 'properties',
    'provider': 'provider',
    'spi': 'spi',
    'spring': 'spring',
    'aop': 'aop',
    'config': 'config',
    'annotation': 'annotation',
}

for src_sub, dst_sub in mapping.items():
    src_dir = src / src_sub
    dst_dir = dst / dst_sub
    if not src_dir.exists():
        continue
    for f in src_dir.iterdir():
        if f.is_file():
            content = f.read_text(encoding='utf-8')
            new_content = content.replace(
                'package com.njydsz.common.ratelimit.',
                'package com.njydsz.common.safe.ratelimit.'
            )
            new_content = new_content.replace(
                'import com.njydsz.common.ratelimit.annotation.RateLimit;',
                'import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;'
            )
            new_content = new_content.replace(
                'import com.njydsz.common.exception.custom.RateLimitException;',
                'import com.njydsz.common.exception.custom.BusinessException;'
            )
            new_content = new_content.replace(
                'import com.njydsz.common.exception.code.RateLimitExceptionCode;',
                ''
            )
            new_content = new_content.replace(
                'RateLimitExceptionCode.API_QPS_LIMIT.getCode()',
                '"D02001"'
            )
            new_content = new_content.replace(
                'throw RateLimitException.builder()',
                'throw BusinessException.builder()'
            )
            if src_sub == 'annotation':
                new_content = new_content.replace('@interface RateLimit', '@interface SentinelRateLimit')
            target = dst_dir / f.name
            target.write_text(new_content, encoding='utf-8')
            print(f'moved {f.name} -> {dst_sub}/{f.name}')

print('Done')
