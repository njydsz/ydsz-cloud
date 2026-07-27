#!/usr/bin/env python3
"""Fix VO class names that incorrectly have DO in them."""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

# literule renames
base = os.path.join(BACKEND, 'ydsz-literule', 'ydsz-literule-domain', 'src', 'main', 'java', 'com', 'njydsz', 'literule', 'domain', 'vo')
renames = [
    ('RuleChainGraphDOVO.java', 'RuleChainGraphVO.java'),
    ('RuleDefinitionDOVO.java', 'RuleDefinitionVO.java'),
    ('RuleExecutionTraceDOVO.java', 'RuleExecutionTraceVO.java'),
    ('RulePackDOVO.java', 'RulePackVO.java'),
    ('RuleTestCaseDOVO.java', 'RuleTestCaseVO.java'),
]
for old, new in renames:
    old_path = os.path.join(base, old)
    new_path = os.path.join(base, new)
    if os.path.exists(old_path):
        with open(old_path, 'r', encoding='utf-8') as f:
            content = f.read()
        # Fix class name in content
        old_cls = old.replace('.java', '')
        new_cls = new.replace('.java', '')
        content = content.replace(old_cls, new_cls)
        content = content.replace('DO 视图', ' 视图')
        with open(new_path, 'w', encoding='utf-8') as f:
            f.write(content)
        os.remove(old_path) if old_path != new_path else None
        print(f'Fixed {old} -> {new}')
    else:
        print(f'Not found: {old}')

# Fix LiteruleConverter imports and references
conv_path = os.path.join(BACKEND, 'ydsz-literule', 'ydsz-literule-domain', 'src', 'main', 'java', 'com', 'njydsz', 'literule', 'domain', 'converter', 'LiteruleConverter.java')
with open(conv_path, 'r', encoding='utf-8') as f:
    content = f.read()
for old_cls, new_cls in [('RuleChainGraphDOVO', 'RuleChainGraphVO'), ('RuleDefinitionDOVO', 'RuleDefinitionVO'), ('RuleExecutionTraceDOVO', 'RuleExecutionTraceVO'), ('RulePackDOVO', 'RulePackVO'), ('RuleTestCaseDOVO', 'RuleTestCaseVO')]:
    content = content.replace(old_cls, new_cls)
with open(conv_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed LiteruleConverter')

# Also fix list method names that had DO in them
with open(conv_path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('RuleChainGraphDOListToVO', 'RuleChainGraphListToVO')
content = content.replace('RuleDefinitionDOListToVO', 'RuleDefinitionListToVO')
content = content.replace('RuleExecutionTraceDOListToVO', 'RuleExecutionTraceListToVO')
content = content.replace('RulePackDOListToVO', 'RulePackListToVO')
content = content.replace('RuleTestCaseDOListToVO', 'RuleTestCaseListToVO')
with open(conv_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed list method names')

print('Done!')
