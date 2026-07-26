#!/usr/bin/env python3
"""Batch add class-level Javadoc to Mapper interfaces and DO entities."""
import pathlib
import re

# Files to process (relative to project root)
USERINFO_FILES = [
    # Mappers
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/CompanyDeptMapper.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/CompanyMapper.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/DepartmentMapper.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/LanguageMapper.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/MenuMapper.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/PostMapper.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/RoleMapper.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/RolePermissionMapper.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/UserAccountMapper.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/UserDeptMapper.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/UserFieldMapper.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/UserPostMapper.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper/UserRoleMapper.java",
    # DOs
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/CompanyDeptDO.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/CompanyDO.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/DepartmentDO.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/LanguageDO.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/MenuDO.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/PostDO.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/RoleDO.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/UserAccountDO.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/UserDeptDO.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/UserFieldDO.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/nydydsz/userinfo/domain/entity/UserPostDO.java",
    "ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/UserRoleDO.java",
]

# Chinese name mappings for entity types
ENTITY_CN_MAP = {
    "Company": "公司",
    "CompanyDept": "公司-部门关联",
    "Department": "部门",
    "Language": "语言",
    "Menu": "菜单",
    "Post": "岗位",
    "Role": "角色",
    "RolePermission": "角色-权限关联",
    "UserAccount": "用户账号",
    "UserDept": "用户-部门关联",
    "UserField": "用户扩展字段",
    "UserPost": "用户-岗位关联",
    "UserRole": "用户-角色关联",
}


def get_cn_name(class_name):
    """Get Chinese name from class name."""
    # Remove DO suffix
    base = class_name.replace("Mapper", "").replace("DO", "")
    return ENTITY_CN_MAP.get(base, base)


def add_javadoc_to_mapper(content, filepath):
    """Add Javadoc to a Mapper interface."""
    # Extract entity name from BaseMapper<XxxDO>
    match = re.search(r'BaseMapper<(\w+)>', content)
    if not match:
        return None
    entity_name = match.group(1)
    cn_name = get_cn_name(entity_name)
    
    # Extract the mapper class name
    mapper_match = re.search(r'public\s+interface\s+(\w+)', content)
    if not mapper_match:
        return None
    mapper_name = mapper_match.group(1)
    
    javadoc = f"""/**
 * {cn_name} Mapper 接口
 *
 * <p>提供对对应数据库表的 CRUD 操作，
 * 继承 MyBatis-Plus BaseMapper 获得基础 CRUD 能力。
 *
 * @since 1.0.0
 */"""
    
    # Insert Javadoc before @Mapper annotation
    if content.startswith("package ") and "@Mapper" in content:
        # Find the line with @Mapper
        lines = content.split("\n")
        new_lines = []
        for line in lines:
            if line.strip() == "@Mapper" and not any("*/" in l for l in new_lines[-5:]):
                new_lines.append(javadoc)
            new_lines.append(line)
        return "\n".join(new_lines)
    return None


def add_javadoc_to_do(content, filepath):
    """Add Javadoc to a DO entity."""
    # Extract table name from @TableName("xxx")
    table_match = re.search(r'@TableName\("(\w+)"\)', content)
    table_name = table_match.group(1) if table_match else "unknown"
    
    # Extract class name
    class_match = re.search(r'public\s+class\s+(\w+)', content)
    if not class_match:
        return None
    class_name = class_match.group(1)
    cn_name = get_cn_name(class_name)
    
    javadoc = f"""/**
 * {cn_name} DO
 *
 * <p>对应数据库表 {{@code {table_name}}}，存储{cn_name}基础信息。
 *
 * @since 1.0.0
 */"""
    
    # Insert Javadoc before the first annotation before class declaration
    # Find all annotations before the class declaration
    lines = content.split("\n")
    class_line_idx = None
    for i, line in enumerate(lines):
        if re.search(r'public\s+class\s+\w+', line):
            class_line_idx = i
            break
    
    if class_line_idx is None:
        return None
    
    # Find the first annotation above the class declaration
    first_annotation_idx = class_line_idx
    for j in range(class_line_idx - 1, max(class_line_idx - 20, -1), -1):
        stripped = lines[j].strip()
        if stripped.startswith("@") or stripped.startswith("import") or stripped.startswith("package"):
            first_annotation_idx = j
        elif stripped and not stripped.startswith("@") and not stripped.startswith("import") and not stripped.startswith("package"):
            break
    
    # Find the first annotation line (skip imports)
    insert_idx = None
    for j in range(first_annotation_idx, class_line_idx + 1):
        if lines[j].strip().startswith("@"):
            insert_idx = j
            break
    
    if insert_idx is None:
        insert_idx = class_line_idx
    
    new_lines = lines[:insert_idx] + javadoc.split("\n") + lines[insert_idx:]
    return "\n".join(new_lines)


def main():
    base = pathlib.Path(".")
    processed = 0
    skipped = 0
    
    for rel_path in USERINFO_FILES:
        filepath = base / rel_path
        if not filepath.exists():
            print(f"SKIP (not found): {rel_path}")
            skipped += 1
            continue
        
        content = filepath.read_text(encoding="utf-8")
        
        # Check if already has Javadoc
        if "*/" in content[:content.find("public")]:
            print(f"SKIP (has Javadoc): {rel_path}")
            skipped += 1
            continue
        
        # Determine if Mapper or DO
        if "Mapper" in filepath.stem:
            new_content = add_javadoc_to_mapper(content, filepath)
        elif filepath.stem.endswith("DO"):
            new_content = add_javadoc_to_do(content, filepath)
        else:
            print(f"SKIP (unknown type): {rel_path}")
            skipped += 1
            continue
        
        if new_content and new_content != content:
            filepath.write_text(new_content, encoding="utf-8")
            print(f"FIXED: {rel_path}")
            processed += 1
        else:
            print(f"SKIP (no change): {rel_path}")
            skipped += 1
    
    print(f"\nProcessed: {processed}, Skipped: {skipped}")


if __name__ == "__main__":
    main()
