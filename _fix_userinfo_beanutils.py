#!/usr/bin/env python3
"""
Replace BeanUtils.copyProperties with MapStruct Converter calls in userinfo ServiceImpl classes.
"""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

# Files to fix
FILES = [
    os.path.join(BACKEND, 'ydsz-userinfo', 'ydsz-userinfo-server', 'src', 'main', 'java',
                 'com', 'njydsz', 'userinfo', 'server', 'service', 'impl', 'PostServiceImpl.java'),
    os.path.join(BACKEND, 'ydsz-userinfo', 'ydsz-userinfo-server', 'src', 'main', 'java',
                 'com', 'njydsz', 'userinfo', 'server', 'service', 'impl', 'UserAccountServiceImpl.java'),
    os.path.join(BACKEND, 'ydsz-userinfo', 'ydsz-userinfo-server', 'src', 'main', 'java',
                 'com', 'njydsz', 'userinfo', 'server', 'service', 'impl', 'RoleServiceImpl.java'),
    os.path.join(BACKEND, 'ydsz-userinfo', 'ydsz-userinfo-server', 'src', 'main', 'java',
                 'com', 'njydsz', 'userinfo', 'server', 'service', 'impl', 'MenuServiceImpl.java'),
    os.path.join(BACKEND, 'ydsz-userinfo', 'ydsz-userinfo-server', 'src', 'main', 'java',
                 'com', 'njydsz', 'userinfo', 'server', 'service', 'impl', 'LanguageServiceImpl.java'),
    os.path.join(BACKEND, 'ydsz-userinfo', 'ydsz-userinfo-server', 'src', 'main', 'java',
                 'com', 'njydsz', 'userinfo', 'server', 'service', 'impl', 'DepartmentServiceImpl.java'),
    os.path.join(BACKEND, 'ydsz-userinfo', 'ydsz-userinfo-server', 'src', 'main', 'java',
                 'com', 'njydsz', 'userinfo', 'server', 'service', 'impl', 'CompanyServiceImpl.java'),
]

for filepath in FILES:
    if not os.path.exists(filepath):
        print(f'Not found: {os.path.basename(filepath)}')
        continue
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    fn = os.path.basename(filepath)
    
    # Add converter import if not present
    if 'UserInfoConverter' not in content and 'BeanUtils.copyProperties' in content:
        # Find last import
        last_import = None
        for m in re.finditer(r'^import .+;$', content, re.MULTILINE):
            last_import = m
        if last_import:
            insert_pos = last_import.end()
            content = content[:insert_pos] + '\nimport com.njydsz.userinfo.domain.converter.UserInfoConverter;' + content[insert_pos:]
    
    # Replace BeanUtils.copyProperties(dto, entity) with Converter.INSTANT.saveDtoToEntity(dto)
    # Pattern: Entity entity = new Entity(); BeanUtils.copyProperties(dto, entity);
    # -> Entity entity = UserInfoConverter.INSTANT.saveDtoToEntity(dto);
    
    # Pattern 1: BeanUtils.copyProperties(dto, entity, "id", "builtIn");
    # This is for update - we can't use saveDtoToEntity because it creates new entity
    # Instead use: UserInfoConverter.INSTANT.saveDtoToEntity(dto) and then copy id
    # But this is complex - let's handle it case by case
    
    # For PostServiceImpl:
    # BeanUtils.copyProperties(dto, entity); -> entity = UserInfoConverter.INSTANT.saveDtoToEntity(dto);
    # But we need to handle the "entity = new Post();" before it
    
    # Actually, let's do targeted replacements per file
    
    if 'PostServiceImpl' in fn:
        # create: Post entity = new Post(); BeanUtils.copyProperties(dto, entity);
        # -> Post entity = UserInfoConverter.INSTANT.saveDtoToEntity(dto);
        content = content.replace(
            'Post entity = new Post();\nBeanUtils.copyProperties(dto, entity);',
            'Post entity = UserInfoConverter.INSTANT.saveDtoToEntity(dto);'
        )
        # update: BeanUtils.copyProperties(dto, entity, "id");
        # For update, we need to map dto to entity but preserve id. MapStruct doesn't do partial updates
        # without @BeanMapping. Let's use a different approach: create new entity from dto, then set id.
        # Actually, we can use @Mapping(target = "id", ignore = true) in saveDtoToEntity and then manually set id.
        # But the update method already has the entity loaded from DB. Let's just keep the BeanUtils for update
        # with ignore properties and replace the toVO method.
        
        # toVO: PostVO vo = new PostVO(); BeanUtils.copyProperties(entity, vo); return vo;
        # -> return UserInfoConverter.INSTANT.entityToVO(entity);
        content = content.replace(
            'PostVO vo = new PostVO();\nBeanUtils.copyProperties(entity, vo);\nreturn vo;',
            'return UserInfoConverter.INSTANT.entityToVO(entity);'
        )
    
    elif 'RoleServiceImpl' in fn:
        content = content.replace(
            'Role entity = new Role();\nBeanUtils.copyProperties(dto, entity);',
            'Role entity = UserInfoConverter.INSTANT.saveDtoToEntity(dto);'
        )
        content = content.replace(
            'RoleVO vo = new RoleVO();\nBeanUtils.copyProperties(entity, vo);\nreturn vo;',
            'return UserInfoConverter.INSTANT.entityToVO(entity);'
        )
    
    elif 'MenuServiceImpl' in fn:
        content = content.replace(
            'Menu entity = new Menu();\nBeanUtils.copyProperties(dto, entity);',
            'Menu entity = UserInfoConverter.INSTANT.saveDtoToEntity(dto);'
        )
        content = content.replace(
            'MenuVO vo = new MenuVO();\nBeanUtils.copyProperties(entity, vo);\nreturn vo;',
            'return UserInfoConverter.INSTANT.entityToVO(entity);'
        )
        # TreeVO: MenuTreeVO vo = new MenuTreeVO(); BeanUtils.copyProperties(menu, vo);
        # -> MenuTreeVO vo = UserInfoConverter.INSTANT.entityToMenuTreeVO(menu);
        content = content.replace(
            'MenuTreeVO vo = new MenuTreeVO();\nBeanUtils.copyProperties(menu, vo);',
            'MenuTreeVO vo = UserInfoConverter.INSTANT.entityToMenuTreeVO(menu);'
        )
    
    elif 'LanguageServiceImpl' in fn:
        content = content.replace(
            'Language entity = new Language();\nBeanUtils.copyProperties(dto, entity);',
            'Language entity = UserInfoConverter.INSTANT.saveDtoToEntity(dto);'
        )
        content = content.replace(
            'LanguageVO vo = new LanguageVO();\nBeanUtils.copyProperties(entity, vo);\nreturn vo;',
            'return UserInfoConverter.INSTANT.entityToVO(entity);'
        )
        # toEntity method
        content = content.replace(
            'Language entity = new Language();\nBeanUtils.copyProperties(dto, entity);\nreturn entity;',
            'return UserInfoConverter.INSTANT.saveDtoToEntity(dto);'
        )
    
    elif 'DepartmentServiceImpl' in fn:
        content = content.replace(
            'Department entity = new Department();\nBeanUtils.copyProperties(dto, entity);',
            'Department entity = UserInfoConverter.INSTANT.saveDtoToEntity(dto);'
        )
        # DepartmentVO vo = new DepartmentVO(); BeanUtils.copyProperties(entity, vo);
        content = content.replace(
            'DepartmentVO vo = new DepartmentVO();\nBeanUtils.copyProperties(entity, vo);',
            'DepartmentVO vo = UserInfoConverter.INSTANT.entityToVO(entity);'
        )
        # TreeVO
        content = content.replace(
            'DepartmentTreeVO vo = new DepartmentTreeVO();\nBeanUtils.copyProperties(entity, vo);',
            'DepartmentTreeVO vo = UserInfoConverter.INSTANT.entityToTreeVO(entity);'
        )
    
    elif 'CompanyServiceImpl' in fn:
        content = content.replace(
            'Company entity = new Company();\nBeanUtils.copyProperties(dto, entity);',
            'Company entity = UserInfoConverter.INSTANT.saveDtoToEntity(dto);'
        )
        content = content.replace(
            'CompanyVO vo = new CompanyVO();\nBeanUtils.copyProperties(entity, vo);',
            'CompanyVO vo = UserInfoConverter.INSTANT.entityToVO(entity);'
        )
    
    elif 'UserAccountServiceImpl' in fn:
        content = content.replace(
            'UserAccount entity = new UserAccount();\nBeanUtils.copyProperties(dto, entity);',
            'UserAccount entity = UserInfoConverter.INSTANT.createDtoToEntity(dto);'
        )
        # toVO
        content = content.replace(
            'UserAccountVO vo = new UserAccountVO();\nBeanUtils.copyProperties(entity, vo);',
            'UserAccountVO vo = UserInfoConverter.INSTANT.entityToVO(entity);'
        )
    
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f'Fixed: {fn}')
    else:
        print(f'No changes: {fn}')

print('\nDone!')
