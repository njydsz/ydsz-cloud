#!/usr/bin/env python3
"""
Batch generate core Java source files for ydsz-userinfo and ydsz-system modules.
Covers P0-3 (common-auth SPI), P1-1 (userinfo CRUD), P1-2 (system CRUD).
"""
import os

BASE = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-userinfo'
BASE_SYS = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-system'


def write_java(path, pkg, imports, body):
    """Write a Java file with package, imports, and body."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    content = f'package {pkg};\n\n'
    if imports:
        content += '\n'.join(imports) + '\n\n'
    content += body
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f'Written: {os.path.basename(path)}')


# ============================================================
# ydsz-userinfo-domain: Entity classes
# ============================================================

DOMAIN_PKG = 'com.njydsz.userinfo.domain.entity'
DOMAIN_PATH = f'{BASE}\\ydsz-userinfo-domain\\src\\main\\java\\com\\njydsz\\userinfo\\domain\\entity'

ENTITY_IMPORTS = [
    'import com.baomidou.mybatisplus.annotation.FieldFill;',
    'import com.baomidou.mybatisplus.annotation.TableField;',
    'import com.baomidou.mybatisplus.annotation.TableId;',
    'import com.baomidou.mybatisplus.annotation.TableLogic;',
    'import com.baomidou.mybatisplus.annotation.TableName;',
    'import lombok.Data;',
    'import java.time.LocalDateTime;',
]

COMMON_FIELDS = '''
    @TableId
    private String id;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private String tenantId;'''

# UserAccountDO
write_java(f'{DOMAIN_PATH}\\UserAccountDO.java', DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_user_account")
public class UserAccountDO {{{COMMON_FIELDS}

    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;
    private String avatar;
    private Integer status;
    private String userType;
    private String companyId;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;
}}
''')

# RoleDO
write_java(f'{DOMAIN_PATH}\\RoleDO.java', DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_role")
public class RoleDO {{{COMMON_FIELDS}

    private String roleCode;
    private String roleName;
    private String description;
    private Integer sortOrder;
    private String status;
    private Boolean builtIn;
}}
''')

# MenuDO
write_java(f'{DOMAIN_PATH}\\MenuDO.java', DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_menu")
public class RoleDO {{{COMMON_FIELDS}

    private String parentId;
    private String menuName;
    private String menuCode;
    private String menuType;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private String permissionCode;
    private Integer visible;
    private String status;
}}
''')

# MenuDO - fix class name
write_java(f'{DOMAIN_PATH}\\MenuDO.java', DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_menu")
public class MenuDO {{{COMMON_FIELDS}

    private String parentId;
    private String menuName;
    private String menuCode;
    private String menuType;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private String permissionCode;
    private Integer visible;
    private String status;
}}
''')

# DepartmentDO
write_java(f'{DOMAIN_PATH}\\DepartmentDO.java', DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_department")
public class DepartmentDO {{{COMMON_FIELDS}

    private String parentId;
    private String deptName;
    private String deptCode;
    private String description;
    private Integer sortOrder;
    private String status;
}}
''')

# CompanyDO
write_java(f'{DOMAIN_PATH}\\CompanyDO.java', DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_company")
public class CompanyDO {{{COMMON_FIELDS}

    private String companyName;
    private String companyCode;
    private String parentId;
    private String contactPerson;
    private String contactPhone;
    private String address;
    private String status;
}}
''')

# PostDO (ydsz_post table)
write_java(f'{DOMAIN_PATH}\\PostDO.java', DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_post")
public class PostDO {{{COMMON_FIELDS}

    private String postName;
    private String postCode;
    private String description;
    private Integer sortOrder;
    private String status;
}}
''')

# LanguageDO
write_java(f'{DOMAIN_PATH}\\LanguageDO.java', DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_language")
public class LanguageDO {{{COMMON_FIELDS}

    private String languageCode;
    private String languageName;
    private Integer isDefault;
    private Integer sortOrder;
    private String status;
}}
''')

# UserDeptDO
write_java(f'{DOMAIN_PATH}\\UserDeptDO.java', DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_user_dept")
public class UserDeptDO {{{COMMON_FIELDS}

    private String userId;
    private String deptId;
    private Integer isPrimary;
}}
''')

# UserPostDO
write_java(f'{DOMAIN_PATH}\\UserPostDO.java', DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_user_post")
public class UserPostDO {{{COMMON_FIELDS}

    private String userId;
    private String postId;
}}
''')

# UserFieldDO
write_java(f'{DOMAIN_PATH}\\UserFieldDO.java', DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_user_field")
public class UserFieldDO {{{COMMON_FIELDS}

    private String userId;
    private String fieldKey;
    private String fieldValue;
    private String fieldType;
}}
''')

# CompanyDeptDO
write_java(f'{DOMAIN_PATH}\\CompanyDeptDO.java', DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_company_dept")
public class CompanyDeptDO {{{COMMON_FIELDS}

    private String companyId;
    private String deptId;
}}
''')

print('\n--- Domain entities done ---')

# ============================================================
# ydsz-userinfo-infra: Mapper interfaces
# ============================================================

INFRA_PKG = 'com.njydsz.userinfo.infra.mapper'
INFRA_PATH = f'{BASE}\\ydsz-userinfo-infra\\src\\main\\java\\com\\njydsz\\userinfo\\infra\\mapper'

MAPPER_IMPORTS = [
    'import com.baomidou.mybatisplus.core.mapper.BaseMapper;',
    f'import {DOMAIN_PKG}.UserAccountDO;',
    'import org.apache.ibatis.annotations.Mapper;',
]

ENTITIES_MAPPER = [
    ('UserAccountMapper', 'UserAccountDO'),
    ('RoleMapper', 'RoleDO'),
    ('MenuMapper', 'MenuDO'),
    ('DepartmentMapper', 'DepartmentDO'),
    ('CompanyMapper', 'CompanyDO'),
    ('PostMapper', 'PostDO'),
    ('LanguageMapper', 'LanguageDO'),
    ('UserDeptMapper', 'UserDeptDO'),
    ('UserPostMapper', 'UserPostDO'),
    ('UserFieldMapper', 'UserFieldDO'),
    ('CompanyDeptMapper', 'CompanyDeptDO'),
]

for cls, entity in ENTITIES_MAPPER:
    imports = [
        'import com.baomidou.mybatisplus.core.mapper.BaseMapper;',
        f'import {DOMAIN_PKG}.{entity};',
        'import org.apache.ibatis.annotations.Mapper;',
    ]
    write_java(f'{INFRA_PATH}\\{cls}.java', INFRA_PKG, imports,
               f'''@Mapper
public interface {cls} extends BaseMapper<{entity}> {{
}}
''')
    # Fix duplicate import in next iteration
    imports.clear()

print('\n--- Infra mappers done ---')

# ============================================================
# ydsz-system-domain: Entity classes
# ============================================================

SYS_DOMAIN_PKG = 'com.njydsz.system.domain.entity'
SYS_DOMAIN_PATH = f'{BASE_SYS}\\ydsz-system-domain\\src\\main\\java\\com\\njydsz\\system\\domain\\entity'

# AppInfoDO
write_java(f'{SYS_DOMAIN_PATH}\\AppInfoDO.java', SYS_DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_app_info")
public class AppInfoDO {{{COMMON_FIELDS}

    private String appCode;
    private String appName;
    private String appKey;
    private String appSecret;
    private String redirectUrl;
    private String description;
    private String status;
}}
''')

# DictTypeDO
write_java(f'{SYS_DOMAIN_PATH}\\DictTypeDO.java', SYS_DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_dict_type")
public class DictTypeDO {{{COMMON_FIELDS}

    private String typeCode;
    private String typeName;
    private String description;
    private String status;
}}
''')

# DictItemDO
write_java(f'{SYS_DOMAIN_PATH}\\DictItemDO.java', SYS_DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_dict_item")
public class DictItemDO {{{COMMON_FIELDS}

    private String typeCode;
    private String itemCode;
    private String itemName;
    private String itemValue;
    private Integer sortOrder;
    private String status;
    private String description;
}}
''')

# ConfigDO (system config)
write_java(f'{SYS_DOMAIN_PATH}\\ConfigDO.java', SYS_DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_config")
public class ConfigDO {{{COMMON_FIELDS}

    private String configGroup;
    private String configKey;
    private String configValue;
    private String valueType;
    private String defaultValue;
    private String description;
    private Integer isPublic;
    private Integer sortOrder;
    private String status;
}}
''')

# VariableDO
write_java(f'{SYS_DOMAIN_PATH}\\VariableDO.java', SYS_DOMAIN_PKG, ENTITY_IMPORTS, f'''@Data
@TableName("ydsz_variable")
public class VariableDO {{{COMMON_FIELDS}

    private String variableKey;
    private String variableValue;
    private String valueType;
    private String description;
    private String status;
}}
''')

print('\n--- System domain entities done ---')

# ============================================================
# ydsz-system-infra: Mapper interfaces
# ============================================================

SYS_INFRA_PKG = 'com.njydsz.system.infra.mapper'
SYS_INFRA_PATH = f'{BASE_SYS}\\ydsz-system-infra\\src\\main\\java\\com\\njydsz\\system\\infra\\mapper'

SYS_ENTITIES_MAPPER = [
    ('AppInfoMapper', 'AppInfoDO'),
    ('DictTypeMapper', 'DictTypeDO'),
    ('DictItemMapper', 'DictItemDO'),
    ('ConfigMapper', 'ConfigDO'),
    ('VariableMapper', 'VariableDO'),
]

for cls, entity in SYS_ENTITIES_MAPPER:
    imports = [
        'import com.baomidou.mybatisplus.core.mapper.BaseMapper;',
        f'import {SYS_DOMAIN_PKG}.{entity};',
        'import org.apache.ibatis.annotations.Mapper;',
    ]
    write_java(f'{SYS_INFRA_PATH}\\{cls}.java', SYS_INFRA_PKG, imports,
               f'''@Mapper
public interface {cls} extends BaseMapper<{entity}> {{
}}
''')
    imports.clear()

print('\n--- System infra mappers done ---')

print('\n========================================')
print('Batch generation complete!')
print(f'Total files written: domain + infra layers')
print('========================================')
