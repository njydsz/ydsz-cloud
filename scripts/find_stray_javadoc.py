import pathlib
import re

base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common')

results = []
for java_file in base.rglob('*.java'):
    if 'target' in java_file.parts:
        continue
    try:
        content = java_file.read_text(encoding='utf-8')
    except:
        continue

    lines = content.split('\n')
    in_annotation = False
    paren_depth = 0

    for i, line in enumerate(lines):
        stripped = line.strip()

        # Track if we're inside an annotation's parentheses
        # Look for lines that have @Annotation( but don't close the paren
        if '@' in stripped and '(' in stripped:
            # Count parens
            opens = stripped.count('(')
            closes = stripped.count(')')
            paren_depth += opens - closes
            if paren_depth > 0:
                in_annotation = True
            continue

        if in_annotation:
            opens = stripped.count('(')
            closes = stripped.count(')')
            paren_depth += opens - closes

            # Check if this line inside an annotation contains a Javadoc start
            if stripped.startswith('/**') or stripped.startswith('*'):
                if stripped.startswith('/**') or (stripped.startswith('*') and not stripped.startswith('*/')):
                    rel = java_file.relative_to(base)
                    results.append((str(rel), i + 1, stripped))
                    break

            if paren_depth <= 0:
                in_annotation = False
                paren_depth = 0

# Also check business modules
biz_base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend')
for mod in ['ydsz-agent', 'ydsz-cronjob', 'ydsz-gateway', 'ydsz-literule',
            'ydsz-message', 'ydsz-nextwiki', 'ydsz-project', 'ydsz-system',
            'ydsz-userinfo', 'ydsz-workflow']:
    mod_path = biz_base / mod
    if not mod_path.exists():
        continue
    for java_file in mod_path.rglob('*.java'):
        if 'target' in java_file.parts:
            continue
        try:
            content = java_file.read_text(encoding='utf-8')
        except:
            continue

        lines = content.split('\n')
        in_annotation = False
        paren_depth = 0

        for i, line in enumerate(lines):
            stripped = line.strip()

            if '@' in stripped and '(' in stripped:
                opens = stripped.count('(')
                closes = stripped.count(')')
                paren_depth += opens - closes
                if paren_depth > 0:
                    in_annotation = True
                continue

            if in_annotation:
                opens = stripped.count('(')
                closes = stripped.count(')')
                paren_depth += opens - closes

                if stripped.startswith('/**'):
                    rel = java_file.relative_to(biz_base)
                    results.append((str(rel), i + 1, stripped))
                    break

                if paren_depth <= 0:
                    in_annotation = False
                    paren_depth = 0

results.sort()
for path, line_num, content in results:
    print(f'{path}:{line_num} → {content}')
print(f'\nTotal: {len(results)} files with stray Javadoc in annotations')
