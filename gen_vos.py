import re, pathlib

base = pathlib.Path('ydsz-backend/ydsz-literule')
vo_dir = base / 'ydsz-literule-domain/src/main/java/com/njydsz/literule/domain/vo'

sources = [
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/cep/CEPHit.java', 'CEPHit', None),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/cep/CEPPattern.java', 'CEPPattern', None),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/approval/ApprovalFlow.java', 'ApprovalFlow', None),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/approval/ApprovalRecord.java', 'ApprovalRecord', None),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/spi/RuleCategoryProvider.java', 'CategoryNode', 'CategoryNode'),
    ('ydsz-literule-api/src/main/java/com/njydsz/literule/api/DecisionTableDefinition.java', 'DecisionTableDefinition', None),
    ('ydsz-literule-api/src/main/java/com/njydsz/literule/api/expr/ExpressionFunctionDef.java', 'ExpressionFunctionDef', None),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/expr/ExpressionPreviewResult.java', 'ExpressionPreviewResult', None),
    ('ydsz-literule-api/src/main/java/com/njydsz/literule/api/expr/ExpressionValidationResult.java', 'ExpressionValidationResult', None),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/spi/RulePackProvider.java', 'InstallResult', 'InstallResult'),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/spi/RulePackProvider.java', 'PackDiff', 'PackDiff'),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/spi/RulePackProvider.java', 'PackUpdateInfo', 'PackUpdateInfo'),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/spi/RuleConflictDetectorProvider.java', 'RuleConflictInfo', 'RuleConflictInfo'),
    ('ydsz-literule-api/src/main/java/com/njydsz/literule/api/RuleEngineStats.java', 'RuleEngineStats', None),
    ('ydsz-literule-api/src/main/java/com/njydsz/literule/api/RuleResult.java', 'RuleResult', None),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/version/RuleVersionDiff.java', 'RuleVersionDiff', None),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/spi/RuleVersion.java', 'RuleVersion', None),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/audit/RuleAuditLogService.java', 'AuditLogEntry', 'AuditLogEntry'),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/dsl/RuleDsl.java', 'RuleDsl', None),
    ('ydsz-literule-server/src/main/java/com/njydsz/literule/server/expr/VariableDefinition.java', 'VariableDefinition', None),
]

all_vos = {}

for src_rel, cls_name, inner_name in sources:
    src_path = base / src_rel
    if not src_path.exists():
        print(f'MISSING: {src_path}')
        continue
    content = src_path.read_text(encoding='utf-8')

    if inner_name:
        pattern = rf'class\s+{inner_name}\b.*?\{{'
        m = re.search(pattern, content)
        if not m:
            pattern = rf'record\s+{inner_name}\b.*?\{{'
            m = re.search(pattern, content)
        if not m:
            print(f'CANNOT FIND INNER CLASS: {inner_name} in {src_path}')
            continue
        start = m.start()
        depth = 0
        end = start
        for i, c in enumerate(content[start:], start):
            if c == '{': depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    end = i + 1
                    break
        content = content[start:end]

    fields = []
    for fm in re.finditer(r'(?:private|public|protected)\s+([^=;\n]+?)\s+(\w+)\s*;', content):
        ftype = fm.group(1).strip()
        fname = fm.group(2).strip()
        if fname in ('serialVersionUID',):
            continue
        fields.append((ftype, fname))

    record_match = re.search(r'record\s+\w+\s*\(([^)]*)\)', content)
    if record_match:
        for comp in record_match.group(1).split(','):
            comp = comp.strip()
            if comp:
                parts = comp.split()
                if len(parts) >= 2:
                    fields.append((' '.join(parts[:-1]).strip(), parts[-1].strip()))

    all_vos[cls_name] = fields
    print(f'=== {cls_name}VO: {len(fields)} fields ===')
    for ft, fn in fields:
        print(f'  {ft} {fn}')

# Generate VO files
imports_map = {
    'LocalDateTime': 'java.time.LocalDateTime',
    'List': 'java.util.List',
    'Map': 'java.util.Map',
    'BigDecimal': 'java.math.BigDecimal',
    'Instant': 'java.time.Instant',
    'Serializable': 'java.io.Serializable',
}

for cls_name, fields in all_vos.items():
    vo_name = cls_name + 'VO'

    needed_imports = set()
    for ftype, fname in fields:
        for key, imp in imports_map.items():
            if key in ftype:
                needed_imports.add(imp)

    lines = []
    lines.append('package com.njydsz.literule.domain.vo;')
    lines.append('')
    for imp in sorted(needed_imports):
        lines.append(f'import {imp};')
    if needed_imports:
        lines.append('')
    lines.append('import lombok.Data;')
    lines.append('')
    lines.append('/**')
    lines.append(f' * {cls_name} 视图对象（VO）。')
    lines.append(' *')
    lines.append(' * @author ydsz-team')
    lines.append(' * @since 1.0.0')
    lines.append(' */')
    lines.append('@Data')
    lines.append(f'public class {vo_name} {{')
    lines.append('')
    for ftype, fname in fields:
        lines.append(f'    /** {fname} */')
        lines.append(f'    private {ftype} {fname};')
        lines.append('')
    lines.append('}')
    lines.append('')

    out_path = vo_dir / f'{vo_name}.java'
    out_path.write_text('\n'.join(lines), encoding='utf-8')
    print(f'GENERATED: {out_path}')

# Generate StringVO (simple wrapper)
string_vo = '''package com.njydsz.literule.domain.vo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * String VO wrapper.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StringVO {
    private String value;
}
'''
(vo_dir / 'StringVO.java').write_text(string_vo, encoding='utf-8')
print('GENERATED: StringVO.java')

# Generate dashboard VOs (copy from api.dto)
dashboard_vos = [
    'RuleDashboardDistributionVO',
    'RuleDashboardOverviewVO',
    'RuleDashboardRealtimeVO',
    'RuleDashboardTopRuleVO',
    'RuleDashboardTrendVO',
]
api_dto_dir = base / 'ydsz-literule-api/src/main/java/com/njydsz/literule/api/dto'
for dv_name in dashboard_vos:
    src = api_dto_dir / f'{dv_name}.java'
    if not src.exists():
        print(f'MISSING dashboard: {src}')
        continue
    content = src.read_text(encoding='utf-8')
    # Change package
    content = content.replace('package com.njydsz.literule.api.dto;', 'package com.njydsz.literule.domain.vo;')
    (vo_dir / f'{dv_name}.java').write_text(content, encoding='utf-8')
    print(f'GENERATED: {dv_name}.java')

print('\nDONE!')
