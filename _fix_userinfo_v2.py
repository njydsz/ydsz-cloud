#!/usr/bin/env python3
"""
Replace remaining BeanUtils.copyProperties calls in userinfo ServiceImpl classes.
Uses regex to handle any indentation.
"""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

FILES = [
    'PostServiceImpl.java',
    'UserAccountServiceImpl.java',
    'RoleServiceImpl.java',
    'MenuServiceImpl.java',
    'LanguageServiceImpl.java',
    'DepartmentServiceImpl.java',
    'CompanyServiceImpl.java',
]

base_dir = os.path.join(BACKEND, 'ydsz-userinfo', 'ydsz-userinfo-server', 'src', 'main', 'java',
                       'com', 'njydsz', 'userinfo', 'server', 'service', 'impl')

for fn in FILES:
    filepath = os.path.join(base_dir, fn)
    if not os.path.exists(filepath):
        print(f'Not found: {fn}')
        continue
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    
    # Pattern 1: Entity entity = new Entity(); (any whitespace) BeanUtils.copyProperties(dto, entity);
    # -> Entity entity = UserInfoConverter.INSTANT.saveDtoToEntity(dto);
    # This works for create methods
    content = re.sub(
        r'(\w+) entity = new \1\(\);\s*\n\s*BeanUtils\.copyProperties\(dto, entity\);',
        r'\1 entity = UserInfoConverter.INSTANT.saveDtoToEntity(dto);',
        content
    )
    
    # Pattern 2: BeanUtils.copyProperties(dto, entity, "id");  (update with ignore)
    # For update, we can't simply use saveDtoToEntity because it creates a new entity
    # and we need to update the existing one. Let's keep this as a TODO comment for now
    # since MapStruct doesn't easily do partial updates without @BeanMapping.
    # Actually, we CAN use MapStruct's @BeanMapping(ignoreByDefault = false) with update methods.
    # But for now, let's just mark these as needing manual migration.
    # Replace the comment about BeanUtils
    content = re.sub(
        r'\* <p>使用 BeanUtils\.copyProperties[^\n]*',
        '* <p>使用 MapStruct 转换（更新操作暂保留 BeanUtils）',
        content
    )
    content = re.sub(
        r'\* 将 DO 转换为 VO，使用 BeanUtils\.copyProperties[^\n]*',
        '* 将 DO 转换为 VO，使用 MapStruct 转换器',
        content
    )
    content = re.sub(
        r'\* 获取对象中值为 null 的属性名数组，用于 BeanUtils\.copyProperties[^\n]*',
        '* 获取对象中值为 null 的属性名数组（用于动态更新忽略 null 值）',
        content
    )
    
    # Pattern 3: XxVO vo = new XxVO(); (any whitespace) BeanUtils.copyProperties(entity, vo); (any whitespace) return vo;
    # -> return UserInfoConverter.INSTANT.entityToVO(entity);
    content = re.sub(
        r'(\w+)VO vo = new \1VO\(\);\s*\n\s*BeanUtils\.copyProperties\(entity, vo\);\s*\n\s*return vo;',
        r'return UserInfoConverter.INSTANT.entityToVO(entity);',
        content
    )
    
    # Pattern 4: MenuTreeVO vo = new MenuTreeVO(); BeanUtils.copyProperties(menu, vo);
    # -> MenuTreeVO vo = UserInfoConverter.INSTANT.entityToMenuTreeVO(menu);
    content = re.sub(
        r'MenuTreeVO vo = new MenuTreeVO\(\);\s*\n\s*BeanUtils\.copyProperties\(menu, vo\);',
        r'MenuTreeVO vo = UserInfoConverter.INSTANT.entityToMenuTreeVO(menu);',
        content
    )
    
    # Pattern 5: DepartmentTreeVO vo = new DepartmentTreeVO(); BeanUtils.copyProperties(entity, vo);
    # -> DepartmentTreeVO vo = UserInfoConverter.INSTANT.entityToTreeVO(entity);
    content = re.sub(
        r'DepartmentTreeVO vo = new DepartmentTreeVO\(\);\s*\n\s*BeanUtils\.copyProperties\(entity, vo\);',
        r'DepartmentTreeVO vo = UserInfoConverter.INSTANT.entityToTreeVO(entity);',
        content
    )
    
    # Pattern 6: CompanyVO vo = new CompanyVO(); BeanUtils.copyProperties(entity, vo);
    content = re.sub(
        r'CompanyVO vo = new CompanyVO\(\);\s*\n\s*BeanUtils\.copyProperties\(entity, vo\);',
        r'CompanyVO vo = UserInfoConverter.INSTANT.entityToVO(entity);',
        content
    )
    
    # Pattern 7: LanguageVO vo = new LanguageVO(); BeanUtils.copyProperties(entity, vo);
    content = re.sub(
        r'LanguageVO vo = new LanguageVO\(\);\s*\n\s*BeanUtils\.copyProperties\(entity, vo\);',
        r'LanguageVO vo = UserInfoConverter.INSTANT.entityToVO(entity);',
        content
    )
    
    # Pattern 8: UserAccountVO vo = new UserAccountVO(); BeanUtils.copyProperties(entity, vo);
    content = re.sub(
        r'UserAccountVO vo = new UserAccountVO\(\);\s*\n\s*BeanUtils\.copyProperties\(entity, vo\);',
        r'UserAccountVO vo = UserInfoConverter.INSTANT.entityToVO(entity);',
        content
    )
    
    # Pattern 9: Language entity = new Language(); BeanUtils.copyProperties(dto, entity); return entity;
    content = re.sub(
        r'Language entity = new Language\(\);\s*\n\s*BeanUtils\.copyProperties\(dto, entity\);\s*\n\s*return entity;',
        r'return UserInfoConverter.INSTANT.saveDtoToEntity(dto);',
        content
    )
    
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f'Fixed: {fn}')
    else:
        print(f'No changes: {fn}')

# Check remaining BeanUtils
print('\n=== Remaining BeanUtils.copyProperties calls ===')
for fn in FILES:
    filepath = os.path.join(base_dir, fn)
    if not os.path.exists(filepath):
        continue
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    count = content.count('BeanUtils.copyProperties')
    # Subtract comment mentions
    comment_count = len(re.findall(r'\*.*BeanUtils\.copyProperties', content))
    actual = count - comment_count
    if actual > 0:
        print(f'  {fn}: {actual} actual calls remaining (update methods with ignore fields)')

print('\nDone!')
