import pathlib
import re

base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-util\src\main\java')

count = 0
for fpath in base.rglob('*.java'):
    text = fpath.read_text(encoding='utf-8')
    original = text
    
    # Fix double @since with optional blank comment line between them
    # Pattern: @since X.Y.Z followed by optional " *\n" lines and another @since
    text = re.sub(
        r'(@since \S+)\s*\n(\s*\*\s*\n)*\s*\*\s*@since \S+',
        r'\1',
        text
    )
    
    if text != original:
        fpath.write_text(text, encoding='utf-8')
        count += 1
        print(f'Fixed: {fpath.relative_to(base)}')

print(f'\nTotal: {count} files fixed')
