import pathlib

root = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend')
old_import = 'import com.njydsz.common.core.health.AbstractModuleHealthIndicator;'
new_import = 'import com.njydsz.common.web.health.AbstractModuleHealthIndicator;'

changed = []
for f in root.rglob('*.java'):
    text = f.read_text(encoding='utf-8')
    if old_import in text:
        new_text = text.replace(old_import, new_import)
        f.write_text(new_text, encoding='utf-8')
        changed.append(str(f))

print(f'已替换 {len(changed)} 个文件:')
for p in changed:
    print('  ', p)
