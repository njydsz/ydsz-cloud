import pathlib
import re

base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-util\src\main\java\com\njydsz\pmis\common\util')

# E2: Check for empty desensitize/ directory
desensitize_dir = base / 'desensitize'
if desensitize_dir.exists():
    files = list(desensitize_dir.rglob('*'))
    if not files:
        desensitize_dir.rmdir()
        print(f'E2: Deleted empty directory {desensitize_dir}')
    else:
        print(f'E2: Directory {desensitize_dir} has {len(files)} files, skipping')
else:
    print(f'E2: Directory {desensitize_dir} does not exist')

# E4: Fix double @since tags
# E5: Clean non-standard @desc annotations
e4_count = 0
e5_count = 0

for fpath in base.rglob('*.java'):
    text = fpath.read_text(encoding='utf-8')
    original = text

    # E4: Fix double @since tags (e.g., "@since 1.0.0\n * @since 3.5.0" -> "@since 1.0.0")
    # Pattern: @since X.Y.Z followed by optional whitespace/newline and another @since
    text = re.sub(
        r'(@since \S+)\s*\n\s*\*\s*@since \S+',
        r'\1',
        text
    )

    # E5: Clean non-standard @desc annotations
    # Pattern: " * @desc Some description" -> remove the line entirely
    # But only if it's a Javadoc comment line (starts with * after whitespace)
    text = re.sub(
        r'^(\s*\*)\s*@desc\s+.*$',
        r'\1',
        text,
        flags=re.MULTILINE
    )
    # Also clean up any resulting empty comment lines that have trailing whitespace
    # But don't remove meaningful empty separator lines

    if text != original:
        fpath.write_text(text, encoding='utf-8')
        if re.search(r'@since \S+\s*\n\s*\*\s*@since \S+', original):
            e4_count += 1
            print(f'E4: Fixed double @since in {fpath.relative_to(base)}')
        if '@desc' in original:
            e5_count += 1
            print(f'E5: Cleaned @desc in {fpath.relative_to(base)}')

print(f'\nSummary: E4 fixed {e4_count} files, E5 cleaned {e5_count} files')
