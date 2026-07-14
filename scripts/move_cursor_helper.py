import pathlib
import shutil

base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-util\src\main\java\com\njydsz\pmis\common\util')

# E1: Move CursorHelper.java to paging/ subpackage
src = base / 'CursorHelper.java'
dst_dir = base / 'paging'
dst_dir.mkdir(exist_ok=True)
dst = dst_dir / 'CursorHelper.java'

text = src.read_text(encoding='utf-8')
text = text.replace('package com.njydsz.pmis.common.util;', 'package com.njydsz.pmis.common.util.paging;')
dst.write_text(text, encoding='utf-8')
src.unlink()
print(f'E1: Moved CursorHelper.java to paging/ package')

# Update external reference in system module
system_base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-system')
for fpath in system_base.rglob('*.java'):
    if 'target' in str(fpath):
        continue
    text = fpath.read_text(encoding='utf-8')
    if 'import com.njydsz.pmis.common.util.CursorHelper;' in text:
        text = text.replace(
            'import com.njydsz.pmis.common.util.CursorHelper;',
            'import com.njydsz.pmis.common.util.paging.CursorHelper;'
        )
        fpath.write_text(text, encoding='utf-8')
        print(f'E1: Updated import in {fpath.relative_to(system_base)}')
