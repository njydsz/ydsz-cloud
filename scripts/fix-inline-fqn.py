#!/usr/bin/env python3
"""
fix-inline-fqn.py — 自动修复行内 FQN 违规

策略：
  1. 解析 fqn-violations.log 提取（文件, 行号, 内容）元组
  2. 按文件分组
  3. 对每个文件：
     a. 解析现有 import 语句（仅顶级类，不处理 import static）
     b. 提取该文件所有违规 FQN
     c. 对每个唯一 FQN：
        - 若简单名已被同包外的不同 FQN 占用：跳过该 FQN（保留 + 加 FQN-OK 注释）
        - 若简单名与同包内类同名：跳过（package 内可见，无需 import）
        - 否则添加 import <FQN>;
     d. 替换行内 FQN 为简单名
     e. 写回文件

例外（不修复）：
  - 字符串字面量中的 FQN（脚本通过 sed 检测，但本脚本不直接处理 log，而是重新扫描源代码）
  - Javadoc {@link FQN} 引用（保留）
  - @ConditionalOnClass(name = "FQN") 字符串参数（保留）
  - 带 // FQN-OK 注释的行（保留）

注意：本脚本只处理 .java 源文件中真实存在的 FQN，不依赖于检测 log。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path
from collections import defaultdict

# FQN 正则：匹配 com.xxx.YyyClass / org.xxx.YyyClass / java.xxx.YyyClass 等
# 要求至少 2 段包名 + 1 段大写开头的类名
FQN_PATTERN = re.compile(
    r'\b(com|org|java|javax|jakarta|net|io)\.'
    r'[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*'
    r'\.[A-Z][a-zA-Z0-9_]*'
)

# 匹配简单名（最后一段）
SIMPLE_NAME_FROM_FQN = re.compile(r'\.([A-Z][a-zA-Z0-9_]*)$')

# import 语句正则
IMPORT_PATTERN = re.compile(r'^import\s+(?:static\s+)?([\w.]+);', re.MULTILINE)
# package 语句正则
PACKAGE_PATTERN = re.compile(r'^package\s+([\w.]+);', re.MULTILINE)

# 跳过行的模式
SKIP_PREFIXES = ('import ', 'package ')

def is_skip_line(line: str) -> bool:
    """判断该行是否应该跳过 FQN 替换（import / package 语句）"""
    stripped = line.lstrip()
    for prefix in SKIP_PREFIXES:
        if stripped.startswith(prefix):
            return True
    return False

def has_fqn_ok_comment(line: str) -> bool:
    """判断该行是否带 FQN-OK 注释（合法的同类名冲突）"""
    return 'FQN-OK' in line

def is_in_string_literal(line: str, fqn_match: re.Match) -> bool:
    """判断 FQN 是否在字符串字面量内"""
    start = fqn_match.start()
    # 统计 FQN 之前的双引号数量（不计算转义的 \"）
    before = line[:start]
    # 移除转义引号
    before_clean = before.replace('\\"', '')
    quote_count = before_clean.count('"')
    return quote_count % 2 == 1

def is_in_javadoc_link(line: str, fqn: str) -> bool:
    """判断 FQN 是否在 {@link FQN} 内"""
    # 提取所有 {@link ...} 内容
    link_pattern = re.compile(r'\{@link\s+([^}]+)\}')
    for m in link_pattern.finditer(line):
        if fqn in m.group(1):
            return True
    return False

def is_in_conditional_on_class(line: str, fqn: str) -> bool:
    """判断是否在 @ConditionalOnClass(name = "FQN") 内"""
    return '@ConditionalOnClass' in line and fqn in line and '"' in line

def is_pure_comment_line(line: str) -> bool:
    """判断是否是纯注释行（以 * 或 // 开头）"""
    stripped = line.lstrip()
    return stripped.startswith('*') or stripped.startswith('//')

def is_javadoc_tag_line_with_fqn(line: str, fqn: str) -> bool:
    """判断是否是 Javadoc 标签行（@throws/@see/@param/@return 后跟 FQN）—— 这是违规，需要修复"""
    stripped = line.lstrip()
    if not (stripped.startswith('*') or stripped.startswith('//')):
        return False
    # 检测 Javadoc 标签
    tag_pattern = re.compile(r'@(throws|see|param|return)\s+' + re.escape(fqn))
    return bool(tag_pattern.search(line))

def parse_imports(content: str) -> dict[str, str]:
    """解析现有 import，返回 {simple_name: fqn} 映射"""
    imports = {}
    for m in IMPORT_PATTERN.finditer(content):
        fqn = m.group(1)
        # 跳过 import static（其简单名可能是方法名，不参与冲突检测）
        # 但仍记录类名
        simple = fqn.rsplit('.', 1)[-1]
        # simple 可能是 *（通配符 import），跳过
        if simple == '*':
            continue
        imports[simple] = fqn
    return imports

def add_import_to_content(content: str, new_imports: list[str]) -> str:
    """将新的 import 语句按字母序插入到现有 import 块中
    
    策略：找到最后一个 import 语句，在其后插入；若无 import，则在 package 行后插入
    """
    if not new_imports:
        return content

    # 找到所有 import 行
    import_lines = []
    for m in re.finditer(r'^(import\s+[\w.]+;)\s*$', content, re.MULTILINE):
        import_lines.append((m.start(), m.end(), m.group(1)))

    new_imports_set = set(new_imports)
    # 去重：避免重复 import 已存在的
    existing_imports = {m.group(1) for m in IMPORT_PATTERN.finditer(content)}
    new_imports_set -= existing_imports
    if not new_imports_set:
        return content

    # 排序新的 import
    sorted_new = sorted(new_imports_set)

    if import_lines:
        # 在最后一个 import 后插入
        last_end = import_lines[-1][1]
        # 检查最后一个 import 后是否有换行
        insert_pos = last_end
        # 跳过可能的注释行
        while insert_pos < len(content) and content[insert_pos] != '\n':
            insert_pos += 1
        # 插入位置在换行符后
        if insert_pos < len(content) and content[insert_pos] == '\n':
            insert_pos += 1
        insert_text = '\n'.join(f'import {fqn};' for fqn in sorted_new) + '\n'
        return content[:insert_pos] + insert_text + content[insert_pos:]
    else:
        # 无 import，在 package 行后插入
        pkg_match = PACKAGE_PATTERN.search(content)
        if pkg_match:
            # 找到 package 行的末尾
            pkg_end = pkg_match.end()
            # 跳过 package 后的换行
            while pkg_end < len(content) and content[pkg_end] != '\n':
                pkg_end += 1
            if pkg_end < len(content) and content[pkg_end] == '\n':
                pkg_end += 1
            insert_text = '\n' + '\n'.join(f'import {fqn};' for fqn in sorted_new) + '\n'
            return content[:pkg_end] + insert_text + content[pkg_end:]
        else:
            # 无 package，在文件开头插入
            insert_text = '\n'.join(f'import {fqn};' for fqn in sorted_new) + '\n\n'
            return insert_text + content

def replace_fqn_in_line(line: str, fqn: str, simple: str) -> str:
    """安全替换行中的 FQN 为简单名
    
    规则：
    1. import / package 行不替换
    2. 带 FQN-OK 注释的行不替换
    3. 字符串字面量中的 FQN 不替换
    4. {@link FQN} 中的 FQN 不替换
    5. @ConditionalOnClass(name="FQN") 中的 FQN 不替换
    6. 纯注释说明行（无 Javadoc 标签）中的 FQN 不替换
    """
    if is_skip_line(line) or has_fqn_ok_comment(line):
        return line

    # 找到 FQN 在行中的所有位置
    result = []
    last_end = 0
    for m in re.finditer(re.escape(fqn), line):
        start = m.start()
        end = m.end()

        # 检查是否在字符串字面量内
        if is_in_string_literal(line[:end], m):
            # 字符串内的 FQN，跳过
            result.append(line[last_end:end])
            last_end = end
            continue

        # 检查是否在 {@link FQN} 内
        if is_in_javadoc_link(line, fqn):
            result.append(line[last_end:end])
            last_end = end
            continue

        # 检查是否在 @ConditionalOnClass(name = "FQN") 内
        if is_in_conditional_on_class(line, fqn):
            result.append(line[last_end:end])
            last_end = end
            continue

        # 检查是否在纯注释行内（无 Javadoc 标签）
        if is_pure_comment_line(line) and not is_javadoc_tag_line_with_fqn(line, fqn):
            # 纯说明性注释行，跳过
            result.append(line[last_end:end])
            last_end = end
            continue

        # 检查 FQN 前后的字符，避免替换子串（如 com.xxx.Foo 不应替换 com.xxx.FooBar）
        # 前一字符不能是 .（表示是更长 FQN 的一部分）
        if start > 0 and line[start - 1] == '.':
            result.append(line[last_end:end])
            last_end = end
            continue
        # 后一字符不能是 . 或字母数字下划线（表示是更长 FQN 或方法调用的一部分）
        if end < len(line) and (line[end] == '.' or line[end].isalnum() or line[end] == '_'):
            result.append(line[last_end:end])
            last_end = end
            continue

        # 安全替换
        result.append(line[last_end:start])
        result.append(simple)
        last_end = end

    result.append(line[last_end:])
    return ''.join(result)


def process_file(file_path: Path, file_violations: list[tuple[int, str]]) -> tuple[int, int]:
    """处理单个文件，返回 (修复数, 跳过数)"""
    try:
        content = file_path.read_text(encoding='utf-8')
    except Exception as e:
        print(f"  ⚠ 无法读取 {file_path}: {e}")
        return (0, 0)

    original_content = content
    existing_imports = parse_imports(content)

    # 第一步：扫描文件中的所有 FQN，并检查每处是否可替换
    # 只为"至少有一处可替换"的 FQN 添加 import
    fqns_to_import: dict[str, str] = {}  # fqn -> simple_name（有可替换出现）
    fqns_to_skip: set[str] = set()  # 因简单名冲突跳过的 FQN
    fqns_only_in_strings: set[str] = set()  # 仅在字符串字面量中出现的 FQN（不添加 import）

    # 收集所有 FQN 及其是否可替换
    all_fqns_with_replaceable: dict[str, bool] = defaultdict(bool)
    for line in content.split('\n'):
        if is_skip_line(line) or has_fqn_ok_comment(line):
            continue
        for m in FQN_PATTERN.finditer(line):
            fqn = m.group(0)
            # 检查是否在字符串字面量内
            if is_in_string_literal(line[:m.end()], m):
                continue
            # 检查是否在 {@link FQN} 内
            if is_in_javadoc_link(line, fqn):
                continue
            # 检查是否在 @ConditionalOnClass(name = "FQN") 内
            if is_in_conditional_on_class(line, fqn):
                continue
            # 检查是否在纯注释行内（无 Javadoc 标签）
            if is_pure_comment_line(line) and not is_javadoc_tag_line_with_fqn(line, fqn):
                continue
            # 检查 FQN 前后字符，避免替换子串
            start, end = m.start(), m.end()
            if start > 0 and line[start - 1] == '.':
                continue
            if end < len(line) and (line[end] == '.' or line[end].isalnum() or line[end] == '_'):
                continue
            # 这是一处可替换的 FQN
            all_fqns_with_replaceable[fqn] = True

    for fqn, replaceable in all_fqns_with_replaceable.items():
        if not replaceable:
            fqns_only_in_strings.add(fqn)
            continue
        simple = fqn.rsplit('.', 1)[-1]
        # 检查简单名是否已被其他 FQN 占用
        if simple in existing_imports and existing_imports[simple] != fqn:
            # 冲突：跳过
            fqns_to_skip.add(fqn)
            continue
        # 检查是否与同文件内已添加的 FQN 冲突
        existing_simple = {s for s in fqns_to_import.values()}
        if simple in existing_simple:
            # 找到已添加的同名 FQN
            for existing_fqn, existing_s in fqns_to_import.items():
                if existing_s == simple and existing_fqn != fqn:
                    fqns_to_skip.add(fqn)
                    break
            continue
        fqns_to_import[fqn] = simple

    # 添加新的 import
    new_imports = list(fqns_to_import.keys())
    content = add_import_to_content(content, new_imports)

    # 替换行内 FQN
    # 需要重新读取内容（因为 import 添加后行号变了）
    lines = content.split('\n')
    fixed_count = 0
    skipped_count = 0

    for i, line in enumerate(lines):
        if FQN_PATTERN.search(line):
            new_line = line
            for fqn, simple in fqns_to_import.items():
                new_line = replace_fqn_in_line(new_line, fqn, simple)
            if new_line != line:
                lines[i] = new_line
                fixed_count += 1
            # 检查跳过的 FQN
            for fqn in fqns_to_skip:
                if fqn in line and not has_fqn_ok_comment(line):
                    skipped_count += 1

    content = '\n'.join(lines)

    if content != original_content:
        file_path.write_text(content, encoding='utf-8')
        return (fixed_count, skipped_count)
    return (0, skipped_count)


def main():
    if len(sys.argv) < 2:
        print("用法: python fix-inline-fqn.py <src-dir> [--apply]")
        print("  默认 dry-run 模式，仅输出计划；--apply 真实写回")
        sys.exit(1)

    src_dir = Path(sys.argv[1])
    apply = '--apply' in sys.argv

    if not src_dir.exists():
        print(f"❌ 目录不存在: {src_dir}")
        sys.exit(1)

    print(f"{'[APPLY]' if apply else '[DRY-RUN]'} 扫描 {src_dir} 下的 .java 文件...")
    print()

    total_files = 0
    total_fixed = 0
    total_skipped = 0

    for java_file in src_dir.rglob('*.java'):
        # 跳过 target 目录
        if 'target' in java_file.parts:
            continue

        # 检查是否包含 FQN
        try:
            content = java_file.read_text(encoding='utf-8')
        except Exception:
            continue

        if not FQN_PATTERN.search(content):
            continue

        # 处理文件
        if apply:
            fixed, skipped = process_file(java_file, [])
            if fixed > 0 or skipped > 0:
                total_files += 1
                total_fixed += fixed
                total_skipped += skipped
                status = f"修复 {fixed} 处" + (f"，跳过 {skipped} 处冲突" if skipped > 0 else "")
                print(f"  ✓ {java_file}: {status}")
        else:
            # dry-run: 统计可替换的 FQN 数量（应用相同过滤逻辑）
            replaceable_fqns: set[str] = set()
            for line in content.split('\n'):
                if is_skip_line(line) or has_fqn_ok_comment(line):
                    continue
                for m in FQN_PATTERN.finditer(line):
                    fqn = m.group(0)
                    if is_in_string_literal(line[:m.end()], m):
                        continue
                    if is_in_javadoc_link(line, fqn):
                        continue
                    if is_in_conditional_on_class(line, fqn):
                        continue
                    if is_pure_comment_line(line) and not is_javadoc_tag_line_with_fqn(line, fqn):
                        continue
                    start, end = m.start(), m.end()
                    if start > 0 and line[start - 1] == '.':
                        continue
                    if end < len(line) and (line[end] == '.' or line[end].isalnum() or line[end] == '_'):
                        continue
                    replaceable_fqns.add(fqn)
            if replaceable_fqns:
                total_files += 1
                total_fixed += len(replaceable_fqns)
                print(f"  📄 {java_file}: 发现 {len(replaceable_fqns)} 个可替换 FQN")

    print()
    print(f"汇总：{'应用' if apply else '扫描'} {total_files} 个文件，"
          f"{'修复' if apply else '发现'} {total_fixed} 处 FQN 违规，跳过 {total_skipped} 处冲突。")
    if not apply:
        print("（dry-run 模式，未实际修改文件。添加 --apply 真实写回。）")


if __name__ == '__main__':
    main()
