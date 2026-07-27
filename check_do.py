#!/usr/bin/env python3
"""Check remaining audit field residues in project DO files."""
import pathlib
import re

BASE = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-project\ydsz-project-domain\src\main\java\com\njydsz\project\domain\entity')
do_files = list(BASE.rglob('*DO.java'))

patterns_to_check = [
    r'@TableId',
    r'@TableLogic',
    r'@Version\b',
    r'private String createdBy;',
    r'private LocalDateTime createdAt;',
    r'private String updatedBy;',
    r'private LocalDateTime updatedAt;',
    r'private Integer deleted;',
    r'private String tenantId;',
    r'private Integer version;',
    r'implements Serializable',
    r'import java\.io\.Serializable',
    r'import java\.time\.LocalDateTime',
    r'import com\.baomidou\.mybatisplus\.annotation\.IdType',
    r'import com\.baomidou\.mybatisplus\.annotation\.TableId',
    r'import com\.baomidou\.mybatisplus\.annotation\.TableLogic',
    r'import com\.baomidou\.mybatisplus\.annotation\.Version',
]

issues = []
for f in do_files:
    content = f.read_text(encoding='utf-8')
    for pattern in patterns_to_check:
        if re.search(pattern, content):
            issues.append((f.name, pattern))
            print(f"  RESIDUE: {f.name} -> {pattern}")

if not issues:
    print("All clean! No residues found.")
else:
    print(f"\nTotal issues: {len(issues)}")
