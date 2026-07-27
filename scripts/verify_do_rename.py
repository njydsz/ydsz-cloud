#!/usr/bin/env python3
"""验证 DO 重命名是否完整，检查是否有遗漏的旧 DO 类名引用。"""
import pathlib
import re

BACKEND = pathlib.Path(r'd:/Code/ydsz/ydsz-pmis/ydsz-backend')

# 6 个保留 DO 后缀的冲突类
CONFLICTS = {
    'AgentDefinitionDO', 'RuleDefinitionDO', 'RuleExecutionTraceDO',
    'RulePackDO', 'RuleChainGraphDO', 'RuleTestCaseDO',
}

# 收集所有已重命名的旧类名 (XxxDO -> Xxx)
# 通过查找当前不存在的 *DO.java 文件来推断旧类名
# 实际上我们直接列出所有已重命名的类名
old_names = set()
for f in BACKEND.rglob('*.java'):
    stem = f.stem
    # 如果文件名不以 DO 结尾，但 XxxDO 版本不存在，说明它被重命名了
    if not stem.endswith('DO'):
        old_name = stem + 'DO'
        old_file = f.parent / f'{old_name}.java'
        if not old_file.exists():
            # Check if this was a renamed DO file by looking for DO references in other files
            old_names.add(old_name)

# Also add base classes
old_names.update(['BaseDO', 'BaseLongDO', 'LogBaseDO'])

# Remove conflicts
old_names -= CONFLICTS

print(f'Checking for {len(old_names)} old DO class names...')

# Search all Java and XML files for remaining references
stale_refs = []
for f in BACKEND.rglob('*'):
    if f.suffix not in ('.java', '.xml'):
        continue
    try:
        content = f.read_text(encoding='utf-8')
    except Exception:
        continue

    for old_name in old_names:
        # Use word boundary to find exact class name references
        pattern = r'\b' + re.escape(old_name) + r'\b'
        matches = list(re.finditer(pattern, content))
        if matches:
            for m in matches:
                # Get line number
                line_num = content[:m.start()].count('\n') + 1
                stale_refs.append((str(f.relative_to(BACKEND)), old_name, line_num))

if stale_refs:
    print(f'\nFOUND {len(stale_refs)} stale references:')
    for path, name, line in stale_refs[:50]:
        print(f'  {path}:{line} -> {name}')
    if len(stale_refs) > 50:
        print(f'  ... and {len(stale_refs) - 50} more')
else:
    print('\nNo stale references found. All DO class names have been properly updated!')

# Also verify that the 6 conflict classes still have their DO files
print('\nConflict classes (should still exist):')
for name in sorted(CONFLICTS):
    found = list(BACKEND.rglob(f'{name}.java'))
    if found:
        print(f'  OK: {name} -> {found[0].relative_to(BACKEND)}')
    else:
        print(f'  MISSING: {name}')
