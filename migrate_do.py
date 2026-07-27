#!/usr/bin/env python3
"""Batch migrate project DO classes to extend MpBaseEntity."""
import pathlib
import re

BASE = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-project\ydsz-project-domain\src\main\java\com\njydsz\project\domain\entity')

do_files = list(BASE.rglob('*DO.java'))
print(f"Found {len(do_files)} DO files")

count = 0
for f in do_files:
    content = f.read_text(encoding='utf-8')
    original = content

    # Replace imports
    old_imports = (
        "import com.baomidou.mybatisplus.annotation.IdType;\n"
        "import com.baomidou.mybatisplus.annotation.TableId;\n"
        "import com.baomidou.mybatisplus.annotation.TableLogic;\n"
        "import com.baomidou.mybatisplus.annotation.TableName;\n"
        "import com.baomidou.mybatisplus.annotation.Version;\n"
    )
    new_imports = (
        "import com.baomidou.mybatisplus.annotation.TableName;\n"
        "import com.njydsz.common.jdbc.entity.MpBaseEntity;\n"
    )
    content = content.replace(old_imports, new_imports)

    # Remove java.io.Serializable import
    content = content.replace("import java.io.Serializable;\n", "")

    # Remove java.time.LocalDateTime import (now in MpBaseEntity)
    content = content.replace("import java.time.LocalDateTime;\n", "")

    # Change class declaration
    content = content.replace("implements Serializable {", "extends MpBaseEntity<String> {")

    # Fix EqualsAndHashCode
    content = content.replace("@EqualsAndHashCode(callSuper = false)", "@EqualsAndHashCode(callSuper = true)")

    # Remove audit fields block (multiple patterns)
    audit_block = re.compile(
        r'    @TableId\(type = IdType\.ASSIGN_ID\)\n'
        r'    private String id;\n'
        r'\n'
        r'    private String createdBy;\n'
        r'    private LocalDateTime createdAt;\n'
        r'    private String updatedBy;\n'
        r'    private LocalDateTime updatedAt;\n'
        r'\n'
        r'    @TableLogic\n'
        r'    private Integer deleted;\n'
        r'\n'
        r'    private String tenantId;\n'
        r'\n'
        r'    @Version\n'
        r'    private Integer version;\n'
    )
    content = audit_block.sub('', content)

    # Clean up extra blank lines after serialVersionUID
    content = re.sub(r'(    private static final long serialVersionUID = 1L;\n)\n\n+', r'\1\n', content)

    # Remove serialVersionUID entirely since MpBaseEntity has it
    content = re.sub(r'\n    private static final long serialVersionUID = 1L;\n', '\n', content)

    if content != original:
        f.write_text(content, encoding='utf-8')
        count += 1
        print(f"  Updated: {f.name}")
    else:
        print(f"  SKIPPED (no change): {f.name}")

print(f"\nTotal updated: {count}/{len(do_files)}")
