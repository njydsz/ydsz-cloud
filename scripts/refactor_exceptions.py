#!/usr/bin/env python3
"""Batch refactor exception sub-classes to use init()/initDefaults() methods."""
import re
import os

BASE = r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-exception\src\main\java\com\njydsz\pmis\common\exception\custom'

# Config for each sub-class: (filename, http_status, level, category, default_code)
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

    # 1. Default constructor: this.httpStatus = X; this.level = Y; this.category = Z;
    # Only matches the 3-line pattern (not followed by this.code)
    default_pattern = (
        '        this.httpStatus = ' + http_status + ';\n'
        '        this.level = ' + level + ';\n'
        '        this.category = ' + category + ';\n'
    )
    default_replacement = '        initDefaults(' + http_status + ', ' + level + ', ' + category + ');\n'
    # Only replace when not followed by this.code (which means it's a default/cause constructor)
    content = re.sub(
        re.escape(default_pattern) + r'(?!\s+this\.code)',
        default_replacement,
        content
    )

    # 2. Throwable cause constructor: 4 lines ending with this.code = DEFAULT_CODE
    cause_pattern = (
        '        this.httpStatus = ' + http_status + ';\n'
        '        this.level = ' + level + ';\n'
        '        this.category = ' + category + ';\n'
        '        this.code = ' + default_code + ';\n'
    )
    cause_replacement = (
        '        initDefaults(' + http_status + ', ' + level + ', ' + category + ');\n'
        '        this.code = ' + default_code + ';\n'
    )
    content = content.replace(cause_pattern, cause_replacement)

    # 3. (String code, Throwable cause) constructor: 4 lines ending with this.code = code;
    code_cause_pattern = (
        '        this.httpStatus = ' + http_status + ';\n'
        '        this.level = ' + level + ';\n'
        '        this.category = ' + category + ';\n'
        '        this.code = code;\n'
    )
    code_cause_replacement = (
        '        initDefaults(' + http_status + ', ' + level + ', ' + category + ');\n'
        '        this.code = code;\n'
    )
    content = content.replace(code_cause_pattern, code_cause_replacement)

    # 4. (ExceptionCode) constructor: 8 lines with exceptionCode fields
    ec_pattern = (
        '        this.httpStatus = exceptionCode.getHttpStatus();\n'
        '        this.level = ' + level + ';\n'
        '        this.category = ' + category + ';\n'
        '        this.code = exceptionCode.getCode();\n'
        '        this.key = exceptionCode.getKey();\n'
        '        this.params = normalizeParams(new Object[]{});\n'
        '        this.message = null;\n'
        '        this.messageKey = exceptionCode.getKey();\n'
        '        this.messageParams = this.params;\n'
    )
    ec_replacement = '        init(exceptionCode, new Object[]{}, ' + level + ', ' + category + ');\n'
    content = content.replace(ec_pattern, ec_replacement)

    # 5. (ExceptionCode, Object[] params) constructor: 8 lines with exceptionCode + params
    ec_params_pattern = (
        '        this.httpStatus = exceptionCode.getHttpStatus();\n'
        '        this.level = ' + level + ';\n'
        '        this.category = ' + category + ';\n'
        '        this.code = exceptionCode.getCode();\n'
        '        this.key = exceptionCode.getKey();\n'
        '        this.params = normalizeParams(params);\n'
        '        this.message = null;\n'
        '        this.messageKey = exceptionCode.getKey();\n'
        '        this.messageParams = this.params;\n'
    )
    ec_params_replacement = '        init(exceptionCode, params, ' + level + ', ' + category + ');\n'
    content = content.replace(ec_params_pattern, ec_params_replacement)

    # 6. (String key) constructor: 8 lines with DEFAULT_CODE + key
    str_key_pattern = (
        '        this.httpStatus = ' + http_status + ';\n'
        '        this.level = ' + level + ';\n'
        '        this.category = ' + category + ';\n'
        '        this.code = ' + default_code + ';\n'
        '        this.key = key;\n'
        '        this.params = normalizeParams(new Object[]{});\n'
        '        this.message = null;\n'
        '        this.messageKey = key;\n'
        '        this.messageParams = this.params;\n'
    )
    str_key_replacement = '        init(' + default_code + ', key, new Object[]{}, ' + http_status + ', ' + level + ', ' + category + ');\n'
    content = content.replace(str_key_pattern, str_key_replacement)

    # 7. (String key, Object[] params) constructor: 8 lines with DEFAULT_CODE + key + params
    str_key_params_pattern = (
        '        this.httpStatus = ' + http_status + ';\n'
        '        this.level = ' + level + ';\n'
        '        this.category = ' + category + ';\n'
        '        this.code = ' + default_code + ';\n'
        '        this.key = key;\n'
        '        this.params = normalizeParams(params);\n'
        '        this.message = null;\n'
        '        this.messageKey = key;\n'
        '        this.messageParams = this.params;\n'
    )
    str_key_params_replacement = '        init(' + default_code + ', key, params, ' + http_status + ', ' + level + ', ' + category + ');\n'
    content = content.replace(str_key_params_pattern, str_key_params_replacement)

    # 8. (String code, String key) constructor: 8 lines with code + key
    code_key_pattern = (
        '        this.httpStatus = ' + http_status + ';\n'
        '        this.level = ' + level + ';\n'
        '        this.category = ' + category + ';\n'
        '        this.code = code;\n'
        '        this.key = key;\n'
        '        this.params = normalizeParams(new Object[]{});\n'
        '        this.message = null;\n'
        '        this.messageKey = key;\n'
        '        this.messageParams = this.params;\n'
    )
    code_key_replacement = '        init(code, key, new Object[]{}, ' + http_status + ', ' + level + ', ' + category + ');\n'
    content = content.replace(code_key_pattern, code_key_replacement)

    # 9. (String code, String key, Object[] params) constructor: 8 lines with code + key + params
    code_key_params_pattern = (
        '        this.httpStatus = ' + http_status + ';\n'
        '        this.level = ' + level + ';\n'
        '        this.category = ' + category + ';\n'
        '        this.code = code;\n'
        '        this.key = key;\n'
        '        this.params = normalizeParams(params);\n'
        '        this.message = null;\n'
        '        this.messageKey = key;\n'
        '        this.messageParams = this.params;\n'
    )
    code_key_params_replacement = '        init(code, key, params, ' + http_status + ', ' + level + ', ' + category + ');\n'
    content = content.replace(code_key_params_pattern, code_key_params_replacement)

    if content != original:
        with open(filepath, 'w', encoding='utf-8', newline='\n') as f:
            f.write(content)
        # Count remaining verbose patterns
        remaining = content.count('this.httpStatus =')
        print(f'  {filename}: REFACTORED (remaining this.httpStatus: {remaining})')
    else:
        print(f'  {filename}: NO CHANGES NEEDED')


print('Starting batch refactoring...')
for cfg in CONFIGS:
    refactor_file(*cfg)
print('Done!')
