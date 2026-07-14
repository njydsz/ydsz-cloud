#!/usr/bin/env python3
"""Remove per-constant @author/@since annotations from HeaderConstants.java."""
import re
import pathlib

p = pathlib.Path(
    r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common'
    r'\ydsz-pmis-common-core\src\main\java\com\njydsz\pmis\common'
    r'\core\constant\HeaderConstants.java'
)
content = p.read_text(encoding='utf-8')
lines = content.split('\n')
result = []
i = 0
while i < len(lines):
    line = lines[i]
    stripped = line.strip()

    # Skip @author ydsz-pmis-team lines (but not the class-level one which is followed by @since and */ at class level)
    # We detect field-level @author by checking if the next non-trivial context is a field declaration
    if stripped == '* @author ydsz-pmis-team':
        # Check if this is a field-level annotation (preceded by */ on a nearby line or field doc)
        # The class-level one is the first occurrence and is followed by @since 1.0.0 then */
        # Field-level ones are followed by @since 1.0.0 then either * or * @see or */
        # We skip all @author lines except the first one (class-level)
        if len(result) > 10:  # Skip after we've passed the class-level Javadoc
            i += 1
            continue

    # Skip field-level @since 1.0.0 lines
    if stripped == '* @since 1.0.0':
        if len(result) > 10:  # Skip after class-level
            i += 1
            continue

    # Remove empty Javadoc continuation lines (lone '*') that precede '*/' after @author/@since removal
    if stripped == '*':
        # Look ahead: if next line is '*/' or '* @see' or another '*', and we just removed @author/@since, skip this
        if i + 1 < len(lines):
            next_stripped = lines[i + 1].strip()
            if next_stripped in ('*/', '* @see ServiceType', '* @see IdentityType', '* @see DataScopeType', ''):
                # Check if previous result line was also '*' or a description ending
                if result and result[-1].strip() in ('*', '*/'):
                    i += 1
                    continue

    result.append(line)
    i += 1

# Clean up: remove lone '*' lines that directly precede '*/' in field Javadocs
final_lines = []
for idx, line in enumerate(result):
    stripped = line.strip()
    if stripped == '*' and idx + 1 < len(result):
        next_stripped = result[idx + 1].strip()
        if next_stripped == '*/':
            # Check this is a field Javadoc (not class-level which has @author/@sinced)
            # Skip this lone '*' line
            continue
    final_lines.append(line)

p.write_text('\n'.join(final_lines), encoding='utf-8')
print(f'Done. Original: {len(lines)} lines, Result: {len(final_lines)} lines')
