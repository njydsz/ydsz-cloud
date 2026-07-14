"""Remove orphaned keys from all messages*.properties files."""
import pathlib

orphaned_keys = {
    'data.integrity.conflict',
    'data.integrity.duplicate',
    'data.integrity.foreign.key',
    'infrastructure.circuit.breaker.open',
    'infrastructure.resource.exhausted',
    'other.system.error',
}

base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-exception\src\main\resources\i18n')
for props_file in base.glob('messages*.properties'):
    lines = props_file.read_text(encoding='utf-8').splitlines()
    new_lines = []
    removed = []
    for line in lines:
        stripped = line.strip()
        if stripped and not stripped.startswith('#') and '=' in stripped:
            key = stripped.split('=')[0].strip()
            if key in orphaned_keys:
                removed.append(key)
                continue
        new_lines.append(line)
    props_file.write_text('\n'.join(new_lines) + '\n', encoding='utf-8')
    print(f'{props_file.name}: removed {len(removed)} keys: {removed}')
