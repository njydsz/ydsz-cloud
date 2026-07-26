#!/usr/bin/env python3
"""Batch add class-level Javadoc to userinfo Mapper and DO files."""
import pathlib

BASE = pathlib.Path('d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-userinfo')
MAPPER_DIR = BASE / 'ydsz-userinfo-infra/src/main/java/com/njydsz/userinfo/infra/mapper'
DO_DIR = BASE / 'ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity'

# (class_name, chinese_name, table_name) for Mappers
MAPPERS = [
    ('CompanyDeptMapper', 'CompanyDeptDO', '公司-部门关联表', 'ydsz_company_dept'),
    ('CompanyMapper', 'CompanyDO', '公司信息', 'ydsz_company'),
    ('DepartmentMapper', 'DepartmentDO', '部门信息', 'ydsz_department'),
    ('LanguageMapper', 'LanguageDO', '语言配置', 'ydsz_language'),
    ('MenuMapper', 'MenuDO', '菜单信息', 'ydsz_menu'),
    ('PostMapper', 'PostDO', '岗位信息', 'ydsz_post'),
    ('RoleMapper', 'RoleDO', '角色信息', 'ydsz_role'),
    ('RolePermissionMapper', 'RolePermissionDO', '角色-权限关联表', 'ydsz_role_permission'),
    ('UserAccountMapper', 'UserAccountDO', '用户账号', 'ydsz_user_account'),
    ('UserDeptMapper', 'UserDeptDO', '用户-部门关联表', 'ydsz_user_dept'),
    ('UserFieldMapper', 'UserFieldDO', '用户自定义字段', 'ydsz_user_field'),
    ('UserPostMapper', 'UserPostDO', '用户-岗位关联表', 'ydsz_user_post'),
    ('UserRoleMapper', 'UserRoleDO', '用户-角色关联表', 'ydsz_user_role'),
]

# (class_name, chinese_name, table_name) for DOs
DOS = [
    ('CompanyDeptDO', '公司-部门关联表', 'ydsz_company_dept'),
    ('CompanyDO', '公司信息', 'ydsz_company'),
    ('DepartmentDO', '部门信息', 'ydsz_department'),
    ('LanguageDO', '语言配置', 'ydsz_language'),
    ('MenuDO', '菜单信息', 'ydsz_menu'),
    ('PostDO', '岗位信息', 'ydsz_post'),
    ('RoleDO', '角色信息', 'ydsz_role'),
    ('RolePermissionDO', '角色-权限关联表', 'ydsz_role_permission'),
    ('UserDeptDO', '用户-部门关联表', 'ydsz_user_dept'),
    ('UserFieldDO', '用户自定义字段', 'ydsz_user_field'),
    ('UserPostDO', '用户-岗位关联表', 'ydsz_user_post'),
    ('UserRoleDO', '用户-角色关联表', 'ydsz_user_role'),
]


def add_mapper_javadoc(filepath, mapper_name, do_name, cn_name, table_name):
    """Add Javadoc before @Mapper annotation."""
    content = filepath.read_text(encoding='utf-8')
    old = '@Mapper\npublic interface ' + mapper_name
    javadoc = '/**\n * ' + cn_name + ' Mapper 接口。\n *\n * <p>对应数据表 ' + table_name + '，\n * 继承 MyBatis-Plus {@code BaseMapper} 提供标准 CRUD 操作。\n *\n * @author ydsz-team\n * @since 1.0.0\n */\n'
    new = javadoc + '@Mapper\npublic interface ' + mapper_name
    if old in content:
        content = content.replace(old, new, 1)
        filepath.write_text(content, encoding='utf-8')
        print(f'  [OK] {mapper_name}')
    else:
        print(f'  [SKIP] {mapper_name} - pattern not found')


def add_do_javadoc(filepath, do_name, cn_name, table_name):
    """Add Javadoc before @Data annotation."""
    content = filepath.read_text(encoding='utf-8')
    old = '@Data\n@SuperBuilder'
    javadoc = '/**\n * ' + cn_name + ' DO 实体。\n *\n * <p>对应数据表 ' + table_name + '，\n * 继承 {@code MpBaseEntity} 提供公共审计字段（id/创建时间/更新时间等）。\n *\n * @author ydsz-team\n * @since 1.0.0\n */\n'
    new = javadoc + '@Data\n@SuperBuilder'
    if old in content:
        content = content.replace(old, new, 1)
        filepath.write_text(content, encoding='utf-8')
        print(f'  [OK] {do_name}')
    else:
        print(f'  [SKIP] {do_name} - pattern not found')


print('=== Processing Mapper files ===')
for mapper_name, do_name, cn_name, table_name in MAPPERS:
    filepath = MAPPER_DIR / (mapper_name + '.java')
    if filepath.exists():
        add_mapper_javadoc(filepath, mapper_name, do_name, cn_name, table_name)
    else:
        print(f'  [NOT FOUND] {mapper_name}')

print()
print('=== Processing DO files ===')
for do_name, cn_name, table_name in DOS:
    filepath = DO_DIR / (do_name + '.java')
    if filepath.exists():
        add_do_javadoc(filepath, do_name, cn_name, table_name)
    else:
        print(f'  [NOT FOUND] {do_name}')

print()
print('Done!')
