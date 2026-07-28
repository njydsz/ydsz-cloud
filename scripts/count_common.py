import pathlib

base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common')
mods = [d for d in sorted(base.iterdir()) if d.is_dir() and (d / 'src' / 'main' / 'java').exists()]
total = 0
for m in mods:
    count = sum(1 for _ in (m / 'src' / 'main' / 'java').rglob('*.java'))
    total += count
    print(f'{m.name}: {count} files')
print(f'\nTotal: {total} files across {len(mods)} modules')
