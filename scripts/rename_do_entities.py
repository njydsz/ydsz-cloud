#!/usr/bin/env python3
"""
批量重命名 DO 实体类：移除 DO 后缀。
- 重命名文件 (XxxDO.java → Xxx.java)
- 更新类声明
- 更新所有 Java 文件中的引用 (import, 类型引用等)
- 更新所有 XML Mapper 文件中的引用 (resultType, type 属性)

排除 6 个命名冲突的 DO 类（与同模块已有非 DO 类同名）。
"""
import pathlib
import re
import sys

BACKEND = pathlib.Path(r'd:/Code/ydsz/ydsz-pmis/ydsz-backend')

# 6 个命名冲突的 DO 类，保留 DO 后缀
CONFLICTS = {
    'AgentDefinitionDO',    # vs AgentDefinition (domain value object)
    'RuleDefinitionDO',     # vs RuleDefinition (API model)
    'RuleExecutionTraceDO', # vs RuleExecutionTrace (API model)
    'RulePackDO',           # vs RulePack (API model)
    'RuleChainGraphDO',     # vs RuleChainGraph (server orchestrator model)
    'RuleTestCaseDO',       # vs RuleTestCase (server testing model)
}


def collect_do_files():
    """收集所有需要重命名的 DO 文件，返回 [(old_path, old_name, new_name)] 列表。"""
    results = []
    for f in BACKEND.rglob('*DO.java'):
        old_name = f.stem  # e.g. UserAccountDO
        if old_name in CONFLICTS:
            print(f'  SKIP (conflict): {old_name} -> {old_name}')
            continue
        new_name = old_name[:-2]  # remove 'DO' suffix
        results.append((f, old_name, new_name))
    return results


def build_regex(mapping):
    """构建一个匹配所有旧类名的正则表达式（按长度降序避免部分匹配）。"""
    sorted_names = sorted(mapping.keys(), key=len, reverse=True)
    pattern = r'\b(' + '|'.join(re.escape(n) for n in sorted_names) + r')\b'
    return re.compile(pattern)


def apply_replacements(content, pattern, mapping):
    """对内容应用所有类名替换。"""
    def replacer(match):
        return mapping[match.group(0)]
    return pattern.sub(replacer, content)


def main():
    print('=' * 60)
    print('DO Entity Renaming Script')
    print('=' * 60)

    # Step 1: Collect DO files
    print('\n[1/5] Collecting DO files...')
    do_files = collect_do_files()
    print(f'  Found {len(do_files)} DO files to rename')
    print(f'  Skipped {len(CONFLICTS)} conflicting DO files')

    # Build mapping: old_name -> new_name
    mapping = {old: new for _, old, new in do_files}
    pattern = build_regex(mapping)

    # Step 2: Rename DO files and update their content
    print('\n[2/5] Renaming DO files and updating class declarations...')
    renamed_count = 0
    old_paths = set()
    for old_path, old_name, new_name in do_files:
        old_paths.add(old_path.resolve())

        # Read content
        content = old_path.read_text(encoding='utf-8')

        # Apply all class name replacements
        new_content = apply_replacements(content, pattern, mapping)

        # Write to new file path
        new_path = old_path.parent / f'{new_name}.java'
        new_path.write_text(new_content, encoding='utf-8')

        # Delete old file
        old_path.unlink()

        renamed_count += 1
    print(f'  Renamed {renamed_count} files')

    # Step 3: Update all other Java files
    print('\n[3/5] Updating references in Java files...')
    java_updated = 0
    for f in BACKEND.rglob('*.java'):
        if f.resolve() in old_paths:
            continue  # Skip already-processed DO files (old paths deleted)
        try:
            content = f.read_text(encoding='utf-8')
        except Exception:
            continue

        new_content = apply_replacements(content, pattern, mapping)
        if new_content != content:
            f.write_text(new_content, encoding='utf-8')
            java_updated += 1
    print(f'  Updated {java_updated} Java files')

    # Step 4: Update all XML mapper files
    print('\n[4/5] Updating references in XML mapper files...')
    xml_updated = 0
    for f in BACKEND.rglob('*.xml'):
        if 'mapper' not in str(f).lower():
            continue
        try:
            content = f.read_text(encoding='utf-8')
        except Exception:
            continue

        new_content = apply_replacements(content, pattern, mapping)
        if new_content != content:
            f.write_text(new_content, encoding='utf-8')
            xml_updated += 1
    print(f'  Updated {xml_updated} XML files')

    # Step 5: Summary
    print('\n[5/5] Summary')
    print(f'  DO files renamed: {renamed_count}')
    print(f'  DO files skipped (conflict): {len(CONFLICTS)}')
    print(f'  Java files updated: {java_updated}')
    print(f'  XML files updated: {xml_updated}')
    print(f'  Total DO files: {renamed_count + len(CONFLICTS)}')

    # Print conflicting classes that were kept
    print('\n  Conflicting DO classes (kept with DO suffix):')
    for name in sorted(CONFLICTS):
        print(f'    - {name}')

    print('\nDone!')


if __name__ == '__main__':
    main()
