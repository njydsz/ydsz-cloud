#!/usr/bin/env python3
"""Remove all @Deprecated code blocks and update references."""
import os
import re
import sys

ROOT = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

# ============================================================
# Phase 1: Delete entirely deprecated files
# ============================================================
FILES_TO_DELETE = [
    r'ydsz-common\ydsz-common-util\src\main\java\com\njydsz\common\util\TraceIdUtil.java',
    r'ydsz-common\ydsz-common-util\src\main\java\com\njydsz\common\util\CryptoSignUtil.java',
    r'ydsz-common\ydsz-common-auth\src\main\java\com\njydsz\common\auth\context\PermissionContextHolder.java',
    r'ydsz-common\ydsz-common-cache\src\main\java\com\njydsz\common\cache\internal\tinylfu\WTinyLFUCache.java',
    r'ydsz-common\ydsz-common-cache\src\main\java\com\njydsz\common\cache\internal\ttl\TTLCache.java',
    r'ydsz-common\ydsz-common-domain\src\main\java\com\njydsz\common\domain\entity\VersionableDO.java',
    r'ydsz-cronjob\ydsz-cronjob-server\src\main\java\com\njydsz\cronjob\server\core\FailStrategy.java',
    r'ydsz-cronjob\ydsz-cronjob-domain\src\main\java\com\njydsz\cronjob\domain\entity\JobRelationDO.java',
    r'ydsz-common\ydsz-common-audit\src\main\java\com\njydsz\common\audit\annotation\DataExportAudit.java',
    r'ydsz-common\ydsz-common-audit\src\main\java\com\njydsz\common\audit\annotation\OperationLog.java',
    r'ydsz-common\ydsz-common-audit\src\main\java\com\njydsz\common\audit\annotation\ApiMetrics.java',
]

def collect_java_files():
    """Collect all .java files under ROOT."""
    java_files = []
    for dirpath, _, filenames in os.walk(ROOT):
        for fn in filenames:
            if fn.endswith('.java'):
                java_files.append(os.path.join(dirpath, fn))
    return java_files

def read_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        return f.read()

def write_file(path, content):
    with open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(content)

def remove_import(content, import_pattern):
    """Remove an import line matching the pattern."""
    lines = content.split('\n')
    new_lines = [l for l in lines if import_pattern not in l]
    return '\n'.join(new_lines)

def replace_import(content, old_import, new_import):
    """Replace an import line."""
    if old_import in content and new_import not in content:
        content = content.replace(old_import, new_import)
    elif old_import in content and new_import in content:
        content = content.replace(old_import + '\n', '')
    return content

# Phase 1: Delete files
print("=== Phase 1: Deleting deprecated files ===")
for rel_path in FILES_TO_DELETE:
    full_path = os.path.join(ROOT, rel_path)
    if os.path.exists(full_path):
        os.remove(full_path)
        print(f"  DELETED: {rel_path}")
    else:
        print(f"  NOT FOUND: {rel_path}")

# ============================================================
# Phase 2: Bulk find-and-replace across all Java files
# ============================================================
print("\n=== Phase 2: Bulk replacements ===")

java_files = collect_java_files()
modified_count = 0

# Define replacement rules: (old_pattern, new_pattern, description)
# These are simple string replacements
SIMPLE_REPLACEMENTS = [
    # TraceIdUtil -> TracerUtils
    ('com.njydsz.common.util.TraceIdUtil', 'com.njydsz.common.util.id.TracerUtils', 'import TraceIdUtil -> TracerUtils'),
    ('TraceIdUtil.', 'TracerUtils.', 'TraceIdUtil. -> TracerUtils.'),
    ('TraceIdUtil.TRACE_ID_KEY', 'TracerUtils.TRACE_ID_KEY', 'TraceIdUtil.TRACE_ID_KEY -> TracerUtils.TRACE_ID_KEY'),

    # CryptoSignUtil -> DigestUtils
    ('com.njydsz.common.util.CryptoSignUtil', 'com.njydsz.common.util.security.DigestUtils', 'import CryptoSignUtil -> DigestUtils'),
    ('CryptoSignUtil.', 'DigestUtils.', 'CryptoSignUtil. -> DigestUtils.'),
    ('CryptoSignUtil.SignatureEncoding', 'DigestUtils.SignatureEncoding', 'CryptoSignUtil.SignatureEncoding -> DigestUtils.SignatureEncoding'),

    # PermissionContextHolder -> AuthContext
    ('com.njydsz.common.auth.context.PermissionContextHolder', 'com.njydsz.common.auth.context.AuthContext', 'import PermissionContextHolder -> AuthContext'),
    ('PermissionContextHolder.', 'AuthContext.', 'PermissionContextHolder. -> AuthContext.'),

    # VersionableDO -> BaseDO
    ('com.njydsz.common.domain.entity.VersionableDO', 'com.njydsz.common.domain.entity.BaseDO', 'import VersionableDO -> BaseDO'),
    ('VersionableDO', 'BaseDO', 'VersionableDO -> BaseDO (class ref)'),

    # FailStrategy -> DagFailureStrategy
    ('com.njydsz.cronjob.server.core.FailStrategy', 'com.njydsz.common.core.dag.DagFailureStrategy', 'import FailStrategy -> DagFailureStrategy'),
    ('FailStrategy.', 'DagFailureStrategy.', 'FailStrategy. -> DagFailureStrategy.'),
    ('FailStrategy ', 'DagFailureStrategy ', 'FailStrategy  -> DagFailureStrategy '),

    # BaseResponse.ok() -> BaseResponse.success()
    ('BaseResponse.ok()', 'BaseResponse.success()', 'BaseResponse.ok() -> success()'),
    # BaseResponse.ok(data) -> BaseResponse.success(data)
    # This needs regex because ok( can be followed by various args
    # We'll handle this with regex below

    # BaseResponse.fail(msg) -> BaseResponse.error(msg)
    # BaseResponse.failed(code, msg) -> BaseResponse.error(code, msg)
    # BaseResponse.failed(resultCode) -> BaseResponse.error(resultCode)
    # BaseResponse.failed(resultCode, msg) -> BaseResponse.error(resultCode, msg)
    # BaseResponse.failed(throwable) -> handled separately

    # RequestContext.capture() -> RequestContext.snapshot()
    ('RequestContext.capture()', 'RequestContext.snapshot()', 'RequestContext.capture() -> snapshot()'),

    # CollectionUtils.arrayToList -> ArrayUtils.toList
    ('com.njydsz.common.util.collection.CollectionUtils.arrayToList', 'com.njydsz.common.util.array.ArrayUtils.toList', 'import arrayToList'),
    ('CollectionUtils.arrayToList', 'ArrayUtils.toList', 'CollectionUtils.arrayToList -> ArrayUtils.toList'),

    # CollectionUtils.isEmpty(Object[]) -> ArrayUtils.isEmpty(Object[])
    # CollectionUtils.isNotEmpty(Object[]) -> ArrayUtils.isNotEmpty(Object[])
    # These are tricky because CollectionUtils also has isEmpty(Collection) etc.
    # We'll handle with regex below

    # PwdUtils.checkPasswordStrengthStr -> PwdUtils.checkPasswordStrength().name()
    ('PwdUtils.checkPasswordStrengthStr', 'PwdUtils.checkPasswordStrength', 'checkPasswordStrengthStr -> checkPasswordStrength'),

    # AuditProperties.getQueueCapacity -> getExecutorQueueCapacity
    ('getQueueCapacity()', 'getExecutorQueueCapacity()', 'getQueueCapacity -> getExecutorQueueCapacity'),
    ('setQueueCapacity(', 'setExecutorQueueCapacity(', 'setQueueCapacity -> setExecutorQueueCapacity'),
]

# Regex replacements (pattern, replacement, description)
import re

REGEX_REPLACEMENTS = [
    # BaseResponse.ok(expr) -> BaseResponse.success(expr)
    (r'BaseResponse\.ok\(', 'BaseResponse.success('),
    # BaseResponse.fail(expr) -> BaseResponse.error(expr)  (but not 'failed')
    (r'BaseResponse\.fail\((?!ed)', 'BaseResponse.error('),
    # BaseResponse.failed(expr) -> BaseResponse.error(expr)
    (r'BaseResponse\.failed\(', 'BaseResponse.error('),

    # RequestContext.getOptional(stringKey) -> use ContextKey version
    # This is tricky - we'll handle separately

    # CacheType.TTL -> remove (handled in CacheBuilder)
    # CacheType.WEAK_KEY -> remove (handled in CacheBuilder)
    # CacheType.WEAK_VALUE -> remove (handled in CacheBuilder)
    # CacheType.SOFT_VALUE -> remove (handled in CacheBuilder)
]

# Annotations to remove (full line including the annotation)
ANNOTATIONS_TO_REMOVE = [
    '@OperationLog',
    '@DataExportAudit',
    '@ApiMetrics',
]

# Imports to remove (exact import lines)
IMPORTS_TO_REMOVE = [
    'import com.njydsz.common.audit.annotation.OperationLog;',
    'import com.njydsz.common.audit.annotation.DataExportAudit;',
    'import com.njydsz.common.audit.annotation.ApiMetrics;',
    'import com.njydsz.common.auth.context.PermissionContextHolder;',
    'import com.njydsz.common.util.TraceIdUtil;',
    'import com.njydsz.common.util.CryptoSignUtil;',
    'import com.njydsz.common.cache.internal.tinylfu.WTinyLFUCache;',
    'import com.njydsz.common.cache.internal.ttl.TTLCache;',
    'import com.njydsz.common.domain.entity.VersionableDO;',
    'import com.njydsz.cronjob.server.core.FailStrategy;',
    'import com.njydsz.cronjob.domain.entity.job.JobRelationDO;',
    'import com.njydsz.cronjob.infra.mapper.job.JobRelationMapper;',
]

for fpath in java_files:
    try:
        content = read_file(fpath)
    except Exception:
        continue

    original = content
    changed = False

    # Remove deprecated import lines
    for imp in IMPORTS_TO_REMOVE:
        if imp in content:
            content = content.replace(imp + '\n', '')
            changed = True

    # Remove deprecated annotation usages (full line)
    lines = content.split('\n')
    new_lines = []
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        # Check if this line is a deprecated annotation usage
        should_remove = False
        for ann in ANNOTATIONS_TO_REMOVE:
            if stripped.startswith(ann + '(') or stripped == ann:
                should_remove = True
                # Check if the annotation spans multiple lines (ends with ))
                if not stripped.endswith(')'):
                    # Consume following lines until we find the closing )
                    j = i + 1
                    while j < len(lines):
                        if ')' in lines[j]:
                            i = j
                            break
                        j += 1
                break

        if not should_remove:
            new_lines.append(line)
        else:
            changed = True
        i += 1

    content = '\n'.join(new_lines)

    # Apply simple replacements
    for old, new, desc in SIMPLE_REPLACEMENTS:
        if old in content:
            content = content.replace(old, new)
            changed = True

    # Apply regex replacements
    for pattern, replacement in REGEX_REPLACEMENTS:
        new_content = re.sub(pattern, replacement, content)
        if new_content != content:
            content = new_content
            changed = True

    if changed and content != original:
        write_file(fpath, content)
        modified_count += 1

print(f"  Modified {modified_count} files")

print("\nDone with bulk replacements.")
print("Individual file edits still needed for complex cases.")
