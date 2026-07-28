import pathlib, re

vo_dir = pathlib.Path('ydsz-backend/ydsz-literule/ydsz-literule-domain/src/main/java/com/njydsz/literule/domain/vo')

# Fix CEPPatternVO - remove duplicate filter field
f = vo_dir / 'CEPPatternVO.java'
content = f.read_text(encoding='utf-8')
lines = content.split('\n')
seen_fields = set()
new_lines = []
skip_next = False
for i, line in enumerate(lines):
    m = re.match(r'\s*private\s+\S+\s+(\w+)\s*;', line)
    if m:
        fname = m.group(1)
        if fname in seen_fields:
            # Remove this line and its preceding comment
            if new_lines and new_lines[-1].strip().startswith('/**'):
                new_lines.pop()
            # Also remove trailing empty line if present
            continue
        seen_fields.add(fname)
    new_lines.append(line)
f.write_text('\n'.join(new_lines), encoding='utf-8')
print('FIXED duplicates: CEPPatternVO.java')

# Fix ExpressionValidationResultVO - replace ErrorType with String
f = vo_dir / 'ExpressionValidationResultVO.java'
content = f.read_text(encoding='utf-8')
content = content.replace('ErrorType', 'String')
f.write_text(content, encoding='utf-8')
print('FIXED: ExpressionValidationResultVO.java')

# Fix RuleEngineStatsVO - replace RuleStat with Object, remove duplicate totalElapsedMs
f = vo_dir / 'RuleEngineStatsVO.java'
content = f.read_text(encoding='utf-8')
content = content.replace('Map<String, RuleStat>', 'Map<String, Object>')
lines = content.split('\n')
seen_fields = set()
new_lines = []
for line in lines:
    m = re.match(r'\s*private\s+\S+\s+(\w+)\s*;', line)
    if m:
        fname = m.group(1)
        if fname in seen_fields:
            if new_lines and new_lines[-1].strip().startswith('/**'):
                new_lines.pop()
            continue
        seen_fields.add(fname)
    new_lines.append(line)
f.write_text('\n'.join(new_lines), encoding='utf-8')
print('FIXED: RuleEngineStatsVO.java')

# Fix RuleResultVO - replace RuleSeverity with String
f = vo_dir / 'RuleResultVO.java'
content = f.read_text(encoding='utf-8')
content = content.replace('RuleSeverity', 'String')
f.write_text(content, encoding='utf-8')
print('FIXED: RuleResultVO.java')

print('\nDONE!')
