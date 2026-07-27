#!/usr/bin/env python3
"""P1-2: Replace all JsonHelper usages with YdszJson/MapUtils in workflow module,
then delete the deprecated JsonHelper class."""

import os
import re

WORKFLOW_SERVER = r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-workflow\ydsz-workflow-server\src\main\java\com\njydsz\workflow\server"

# Replacement rules: old pattern → new pattern
REPLACEMENTS = [
    # Import replacements
    (r'import com\.njydsz\.workflow\.server\.engine\.JsonHelper;', ''),
    (r'import com\.njydsz\.common\.util\.collection\.MapUtils;', 'import com.njydsz.common.util.collection.MapUtils;\nimport com.njydsz.common.json.YdszJson;'),

    # Method replacements
    (r'JsonHelper\.toJson\(', 'YdszJson.toJson('),
    (r'JsonHelper\.fromJson\(', 'YdszJson.parseMap('),
    (r'JsonHelper\.getString\(', 'MapUtils.getString('),
    (r'JsonHelper\.getInteger\(', 'MapUtils.getInteger('),
    (r'JsonHelper\.getLong\(', 'MapUtils.getLong('),
    (r'JsonHelper\.getMap\(', 'MapUtils.getMap('),
    (r'JsonHelper\.getList\(', 'MapUtils.getList('),
    (r'JsonHelper\.getMapFromList\(', 'MapUtils.getMapFromList('),
    (r'JsonHelper\.toStringObjectMap\(', 'MapUtils.toStringObjectMap('),
    (r'JsonHelper\.safeCastList\(', 'MapUtils.safeCastList('),
    (r'JsonHelper\.safeCastMap\(', 'MapUtils.safeCastMap('),
    (r'JsonHelper\.getListOfMaps\(', 'MapUtils.getListOfMaps('),
]


def find_java_files(base_dir):
    result = []
    for root, dirs, files in os.walk(base_dir):
        for f in files:
            if f.endswith('.java'):
                result.append(os.path.join(root, f))
    return result


def apply_replacements(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    for old, new in REPLACEMENTS:
        content = re.sub(old, new, content)

    # Clean up duplicate imports if MapUtils was already imported
    # Remove duplicate YdszJson imports
    lines = content.split('\n')
    seen_imports = set()
    deduped = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith('import ') and stripped in seen_imports:
            continue
        if stripped.startswith('import '):
            seen_imports.add(stripped)
        deduped.append(line)
    content = '\n'.join(deduped)

    if content != original:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False


def main():
    java_files = find_java_files(WORKFLOW_SERVER)
    modified = 0
    for java_file in java_files:
        if apply_replacements(java_file):
            modified += 1
            print(f"MODIFIED: {java_file}")

    # Delete the deprecated JsonHelper class
    json_helper_path = os.path.join(WORKFLOW_SERVER, 'engine', 'JsonHelper.java')
    if os.path.exists(json_helper_path):
        os.remove(json_helper_path)
        print(f"DELETED: {json_helper_path}")

    print(f"\nTotal modified: {modified} files")


if __name__ == '__main__':
    main()
