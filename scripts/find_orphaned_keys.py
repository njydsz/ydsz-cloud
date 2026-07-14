"""Find orphaned keys in messages.properties not referenced by any Java source."""
import pathlib
import re

props_file = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-exception\src\main\resources\i18n\messages.properties')
java_base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-exception\src')

# Read all keys from properties file
keys = set()
for line in props_file.read_text(encoding='utf-8').splitlines():
    line = line.strip()
    if line and not line.startswith('#') and '=' in line:
        key = line.split('=')[0].strip()
        if key:
            keys.add(key)

# Read all Java source files
all_java_content = ""
for f in java_base.rglob('*.java'):
    all_java_content += f.read_text(encoding='utf-8')

# Check which keys are referenced
orphaned = []
for key in sorted(keys):
    if key not in all_java_content:
        orphaned.append(key)

print(f"Total keys in messages.properties: {len(keys)}")
print(f"Orphaned keys (not in any Java source): {len(orphaned)}")
for k in orphaned:
    print(f"  - {k}")
