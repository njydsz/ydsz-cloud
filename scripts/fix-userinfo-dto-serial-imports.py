"""修复 ydsz-userinfo-domain DTO 缺失 Serializable / @Serial 导入问题。

12 个 DTO 文件使用了 `implements Serializable` 和 `@Serial` 注解，
但未导入 `java.io.Serializable` 和 `java.io.Serial`，导致编译失败。

本脚本扫描 ydsz-userinfo-domain 下所有 DTO 文件，统一补充缺失的导入。
"""

from __future__ import annotations

import pathlib
import re

DTO_ROOT = pathlib.Path(r"d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/dto")


def fix_file(path: pathlib.Path) -> bool:
    """若文件使用 Serializable/@Serial 但未导入，则补充导入。返回是否修改。"""
    content = path.read_text(encoding="utf-8")
    if "Serializable" not in content and "@Serial" not in content:
        return False

    needs_serializable = "implements Serializable" in content and "import java.io.Serializable;" not in content
    needs_serial = "@Serial" in content and "import java.io.Serial;" not in content

    if not needs_serializable and not needs_serial:
        return False

    # 找到最后一个 import 语句的位置，将新导入插入其后
    import_pattern = re.compile(r"^import\s+[a-zA-Z0-9_.]+;\s*$", re.MULTILINE)
    matches = list(import_pattern.finditer(content))
    if not matches:
        # 没有 import 语句，在 package 行之后插入
        pkg_match = re.search(r"^package\s+[a-zA-Z0-9_.]+;\s*$", content, re.MULTILINE)
        if not pkg_match:
            return False
        insert_pos = pkg_match.end()
        # 跳过空行
        while insert_pos < len(content) and content[insert_pos] in "\r\n":
            insert_pos += 1
        new_imports = []
        if needs_serializable:
            new_imports.append("import java.io.Serializable;")
        if needs_serial:
            new_imports.append("import java.io.Serial;")
        new_block = "\n".join(new_imports) + "\n\n"
        new_content = content[:insert_pos] + new_block + content[insert_pos:]
    else:
        last_import_end = matches[-1].end()
        new_imports = []
        if needs_serializable:
            new_imports.append("import java.io.Serializable;")
        if needs_serial:
            new_imports.append("import java.io.Serial;")
        new_block = "\n" + "\n".join(new_imports)
        new_content = content[:last_import_end] + new_block + content[last_import_end:]

    if new_content == content:
        return False
    path.write_text(new_content, encoding="utf-8")
    return True


def main() -> None:
    print("=== 修复 ydsz-userinfo-domain DTO 缺失 Serializable 导入 ===")
    fixed_count = 0
    for java_file in DTO_ROOT.rglob("*.java"):
        if fix_file(java_file):
            print(f"  修复: {java_file.relative_to(pathlib.Path(r'd:/Code/ydsz/ydsz-pmis/ydsz-backend'))}")
            fixed_count += 1
    print(f"\n共修复 {fixed_count} 个文件")


if __name__ == "__main__":
    main()
