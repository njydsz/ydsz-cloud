#!/usr/bin/env python3
"""Remove duplicate @since 1.0.0 Javadoc tags from Java files."""
import pathlib
import re

base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-queue\src\main\java')
since_pattern = re.compile(r' \* @since 1\.0\.0\s*$')
total_removed = 0

for f in base.rglob('*.java'):
    text = f.read_text(encoding='utf-8')
    lines = text.split('\n')
    new_lines = []
    prev_was_since = False
    removed = 0
    for line in lines:
        is_since = bool(since_pattern.match(line))
        if is_since and prev_was_since:
            removed += 1
            continue
        new_lines.append(line)
        prev_was_since = is_since
    if removed > 0:
        f.write_text('\n'.join(new_lines), encoding='utf-8')
        total_removed += removed
        print(f'{f.name}: removed {removed} duplicate(s)')

print(f'\nTotal: removed {total_removed} duplicate @since tags')
