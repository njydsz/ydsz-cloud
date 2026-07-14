import pathlib
import re

base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend')

# Find all files that import SnowflakeIdGenerator
files = []
for fpath in base.rglob('*.java'):
    if 'target' in str(fpath):
        continue
    text = fpath.read_text(encoding='utf-8')
    if 'import com.njydsz.pmis.common.util.SnowflakeIdGenerator;' in text:
        files.append(fpath)

print(f'Found {len(files)} files to update')

for fpath in files:
    text = fpath.read_text(encoding='utf-8')
    
    # Replace import
    text = text.replace(
        'import com.njydsz.pmis.common.util.SnowflakeIdGenerator;',
        'import com.njydsz.pmis.common.util.id.SnowflakeUtils;'
    )
    
    # Replace method calls
    text = text.replace('SnowflakeIdGenerator.nextIdStr()', 'SnowflakeUtils.nextIdStr()')
    text = text.replace('SnowflakeIdGenerator.nextId()', 'SnowflakeUtils.nextIdLong()')
    text = text.replace('SnowflakeIdGenerator.nextTraceId()', 'SnowflakeUtils.nextIdStr()')
    
    fpath.write_text(text, encoding='utf-8')
    print(f'OK: {fpath.relative_to(base)}')
