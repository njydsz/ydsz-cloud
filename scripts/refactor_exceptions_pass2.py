#!/usr/bin/env python3
"""Second pass: replace initDefaults + verbose field assignments with init()."""
import re
import os

BASE = r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-exception\src\main\java\com\njydsz\pmis\common\exception\custom'

CONFIGS = [
    ('SysException.java', 'HttpStatus.INTERNAL_SERVER_ERROR.value()', 'ExceptionLevel.ERROR', 'ExceptionCategory.SYSTEM', 'UnifiedExceptionCode.INTERNAL_ERROR.getCode()'),
    ('ValidationException.java', 'HttpStatus.BAD_REQUEST.value()', 'ExceptionLevel.WARN', 'ExceptionCategory.VALIDATION', 'UnifiedExceptionCode.PARAM_ERROR.getCode()'),
    ('CircuitBreakerException.java', 'HttpStatus.SERVICE_UNAVAILABLE.value()', 'ExceptionLevel.ERROR', 'ExceptionCategory.INFRASTRUCTURE', 'UnifiedExceptionCode.CIRCUIT_BREAKER_OPEN.getCode()'),
    ('ConcurrencyException.java', 'HttpStatus.CONFLICT.value()', 'ExceptionLevel.WARN', 'ExceptionCategory.CONCURRENCY', 'UnifiedExceptionCode.FAIL.getCode()'),
    ('DegradeException.java', 'HttpStatus.SERVICE_UNAVAILABLE.value()', 'ExceptionLevel.WARN', 'ExceptionCategory.INFRASTRUCTURE', 'UnifiedExceptionCode.SERVICE_DEGRADED.getCode()'),
    ('DuplicateException.java', 'HttpStatus.CONFLICT.value()', 'ExceptionLevel.WARN', 'ExceptionCategory.DUPLICATE', 'UnifiedExceptionCode.FAIL.getCode()'),
    ('ExternalException.java', 'HttpStatus.BAD_GATEWAY.value()', 'ExceptionLevel.ERROR', 'ExceptionCategory.EXTERNAL', 'UnifiedExceptionCode.FAIL.getCode()'),
    ('InfrastructureException.java', 'HttpStatus.INTERNAL_SERVER_ERROR.value()', 'ExceptionLevel.ERROR', 'ExceptionCategory.INFRASTRUCTURE', 'UnifiedExceptionCode.INTERNAL_ERROR.getCode()'),
    ('RateLimitException.java', 'HttpStatus.TOO_MANY_REQUESTS.value()', 'ExceptionLevel.WARN', 'ExceptionCategory.RATE_LIMIT', 'UnifiedExceptionCode.FAIL.getCode()'),
    ('YdszSecurityException.java', 'HttpStatus.FORBIDDEN.value()', 'ExceptionLevel.WARN', 'ExceptionCategory.SECURITY', 'UnifiedExceptionCode.FORBIDDEN.getCode()'),
    ('YdszTimeoutException.java', 'HttpStatus.GATEWAY_TIMEOUT.value()', 'ExceptionLevel.ERROR', 'ExceptionCategory.TIMEOUT', 'UnifiedExceptionCode.FAIL.getCode()'),
]


def refactor_file(filename, http_status, level, category, default_code):
    filepath = os.path.join(BASE, filename)
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    init_defaults = 'initDefaults(' + http_status + ', ' + level + ', ' + category + ');'

    # Pattern 1: initDefaults + DEFAULT_CODE + key + empty params
    # initDefaults(...);\n        this.code = DEFAULT_CODE;\n        this.key = key;\n        this.params = normalizeParams(new Object[]{});\n        this.message = null;\n        this.messageKey = key;\n        this.messageParams = this.params;
    p1 = (
        '        ' + init_defaults + '\n'
        '        this.code = ' + default_code + ';\n'
        '        this.key = key;\n'
        '        this.params = normalizeParams(new Object[]{});\n'
        '        this.message = null;\n'
        '        this.messageKey = key;\n'
        '        this.messageParams = this.params;\n'
    )
    r1 = '        init(' + default_code + ', key, new Object[]{}, ' + http_status + ', ' + level + ', ' + category + ');\n'
    content = content.replace(p1, r1)

    # Pattern 2: initDefaults + DEFAULT_CODE + key + params
    p2 = (
        '        ' + init_defaults + '\n'
        '        this.code = ' + default_code + ';\n'
        '        this.key = key;\n'
        '        this.params = normalizeParams(params);\n'
        '        this.message = null;\n'
        '        this.messageKey = key;\n'
        '        this.messageParams = this.params;\n'
    )
    r2 = '        init(' + default_code + ', key, params, ' + http_status + ', ' + level + ', ' + category + ');\n'
    content = content.replace(p2, r2)

    # Pattern 3: initDefaults + code + key + empty params
    p3 = (
        '        ' + init_defaults + '\n'
        '        this.code = code;\n'
        '        this.key = key;\n'
        '        this.params = normalizeParams(new Object[]{});\n'
        '        this.message = null;\n'
        '        this.messageKey = key;\n'
        '        this.messageParams = this.params;\n'
    )
    r3 = '        init(code, key, new Object[]{}, ' + http_status + ', ' + level + ', ' + category + ');\n'
    content = content.replace(p3, r3)

    # Pattern 4: initDefaults + code + key + params
    p4 = (
        '        ' + init_defaults + '\n'
        '        this.code = code;\n'
        '        this.key = key;\n'
        '        this.params = normalizeParams(params);\n'
        '        this.message = null;\n'
        '        this.messageKey = key;\n'
        '        this.messageParams = this.params;\n'
    )
    r4 = '        init(code, key, params, ' + http_status + ', ' + level + ', ' + category + ');\n'
    content = content.replace(p4, r4)

    # Pattern 5: initDefaults + DEFAULT_CODE (cause constructor - already has initDefaults + this.code)
    # This is the Throwable cause constructor that was partially refactored
    p5 = (
        '        ' + init_defaults + '\n'
        '        this.code = ' + default_code + ';\n'
    )
    # This is correct - cause constructors only set defaults + code
    # No further change needed

    # Pattern 6: initDefaults + code (String code, Throwable cause constructor)
    p6 = (
        '        ' + init_defaults + '\n'
        '        this.code = code;\n'
    )
    # This is correct - no further change needed

    if content != original:
        with open(filepath, 'w', encoding='utf-8', newline='\n') as f:
            f.write(content)
        remaining = content.count('this.key =')
        print(f'  {filename}: REFACTORED (remaining this.key: {remaining})')
    else:
        remaining = content.count('this.key =')
        print(f'  {filename}: NO CHANGES (remaining this.key: {remaining})')


print('Starting second pass refactoring...')
for cfg in CONFIGS:
    refactor_file(*cfg)
print('Done!')
