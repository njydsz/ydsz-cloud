import pathlib, re

vo_dir = pathlib.Path('ydsz-backend/ydsz-literule/ydsz-literule-domain/src/main/java/com/njydsz/literule/domain/vo')

# Types from server module that domain can't access -> replace with Object or String
server_complex_types = {
    'CEPEvent', 'SequenceStep', 'ApprovalStep', 'FieldDiff',
    'DiffEntry', 'RuleDslEntry', 'ChainDslEntry',
    'Column', 'Row',
}
server_enum_types = {
    'PatternType', 'WindowType', 'AggregateFunction',
    'AuditAction', 'AuditResult', 'DiffType',
}

# Java types that need imports
java_imports = {
    'Duration': 'java.time.Duration',
    'Instant': 'java.time.Instant',
    'LocalDateTime': 'java.time.LocalDateTime',
    'List': 'java.util.List',
    'Map': 'java.util.Map',
    'BigDecimal': 'java.math.BigDecimal',
    'Serializable': 'java.io.Serializable',
}

for vo_file in vo_dir.glob('*.java'):
    content = vo_file.read_text(encoding='utf-8')
    changed = False

    # Replace server complex types with Object
    for t in server_complex_types:
        if re.search(r'\b' + t + r'\b', content):
            content = re.sub(r'\b' + t + r'\b', 'Object', content)
            changed = True

    # Replace server enum types with String
    for t in server_enum_types:
        if re.search(r'\b' + t + r'\b', content):
            content = re.sub(r'\b' + t + r'\b', 'String', content)
            changed = True

    if changed:
        vo_file.write_text(content, encoding='utf-8')
        print(f'FIXED: {vo_file.name}')

# Fix CEPPatternVO duplicate eventType field
cep_path = vo_dir / 'CEPPatternVO.java'
if cep_path.exists():
    content = cep_path.read_text(encoding='utf-8')
    # Remove the second eventType field block
    lines = content.split('\n')
    seen_eventType = False
    new_lines = []
    for line in lines:
        if 'eventType' in line and 'private' in line:
            if seen_eventType:
                # Skip this duplicate and its comment
                if new_lines and new_lines[-1].strip().startswith('/**'):
                    new_lines.pop()
                continue
            seen_eventType = True
        new_lines.append(line)
    cep_path.write_text('\n'.join(new_lines), encoding='utf-8')
    print('FIXED duplicates: CEPPatternVO.java')

# Fix missing imports in all VOs
for vo_file in vo_dir.glob('*.java'):
    content = vo_file.read_text(encoding='utf-8')
    needed = set()
    for key, imp in java_imports.items():
        if re.search(r'\b' + key + r'\b', content) and f'import {imp}' not in content:
            needed.add(imp)

    if needed:
        # Insert imports after package line
        lines = content.split('\n')
        insert_idx = 1
        for i, line in enumerate(lines):
            if line.startswith('package '):
                insert_idx = i + 1
                break

        existing_imports = set()
        for line in lines:
            if line.startswith('import '):
                existing_imports.add(line.strip())

        new_imports = []
        for imp in sorted(needed):
            stmt = f'import {imp};'
            if stmt not in existing_imports:
                new_imports.append(stmt)

        if new_imports:
            # Find where to insert (after existing imports or after package)
            insert_after = 0
            for i, line in enumerate(lines):
                if line.startswith('import '):
                    insert_after = i
            if insert_after == 0:
                insert_after = insert_idx

            lines.insert(insert_after + 1, '')
            for j, imp in enumerate(new_imports):
                lines.insert(insert_after + 2 + j, imp)

            content = '\n'.join(lines)
            vo_file.write_text(content, encoding='utf-8')
            print(f'ADDED IMPORTS: {vo_file.name}: {new_imports}')

# Delete unnecessary dashboard VOs (using api.dto versions directly)
dashboard_vos = [
    'RuleDashboardDistributionVO.java',
    'RuleDashboardOverviewVO.java',
    'RuleDashboardRealtimeVO.java',
    'RuleDashboardTopRuleVO.java',
    'RuleDashboardTrendVO.java',
]
for dv in dashboard_vos:
    p = vo_dir / dv
    if p.exists():
        p.unlink()
        print(f'DELETED: {dv}')

print('\nDONE!')
