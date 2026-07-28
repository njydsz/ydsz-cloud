#!/usr/bin/env python3
"""
YDSZ 后端代码生成器 — DTO/VO/Converter 脚手架

P2-2: 根据 Entity 类名自动生成 PostDTO/PutDTO/VO + Converter 方法 + Service 接口方法签名

用法:
  python deploy/scripts/gen-dto-vo.py --entity Department --package userinfo
  python deploy/scripts/gen-dto-vo.py --entity Project --package project --output-dir ydsz-backend

@author ydsz-team
@since 1.0.0
"""
import argparse
import pathlib
import re
import sys


def to_camel(snake: str) -> str:
    """snake_case → camelCase"""
    parts = snake.split('_')
    return parts[0] + ''.join(p.title() for p in parts[1:])


def to_pascal(snake: str) -> str:
    """snake_case → PascalCase"""
    return ''.join(p.title() for p in snake.split('_'))


def find_entity_fields(entity_path: pathlib.Path) -> list[dict]:
    """从 Entity Java 文件中提取字段信息"""
    if not entity_path.exists():
        return []

    content = entity_path.read_text(encoding='utf-8')
    fields = []
    # 匹配 private 类型 字段名;  格式
    pattern = r'private\s+([\w<>]+)\s+(\w+)\s*;'
    for match in re.finditer(pattern, content):
        java_type = match.group(1)
        field_name = match.group(2)
        # 跳过审计字段（由基类管理）
        if field_name in ('id', 'createTime', 'updateTime', 'createBy', 'updateBy',
                          'deleted', 'tenantId', 'version'):
            continue
        fields.append({'type': java_type, 'name': field_name})
    return fields


def generate_post_dto(entity_name: str, package: str, fields: list[dict]) -> str:
    """生成 PostDTO"""
    fields_str = '\n'.join(
        f'    private {f["type"]} {f["name"]};' for f in fields
    )
    return f'''package com.njydsz.{package}.domain.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * {entity_name} 新增请求 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "{entity_name}新增请求")
public class {entity_name}PostDTO {{

{fields_str}
}}
'''


def generate_put_dto(entity_name: str, package: str, fields: list[dict]) -> str:
    """生成 PutDTO"""
    fields_str = '\n'.join(
        f'    private {f["type"]} {f["name"]};' for f in fields
    )
    return f'''package com.njydsz.{package}.domain.dto.put;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * {entity_name} 更新请求 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "{entity_name}更新请求")
public class {entity_name}PutDTO {{

    private String id;

{fields_str}
}}
'''


def generate_vo(entity_name: str, package: str, fields: list[dict]) -> str:
    """生成 VO"""
    fields_str = '\n'.join(
        f'    private {f["type"]} {f["name"]};' for f in fields
    )
    return f'''package com.njydsz.{package}.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * {entity_name} 视图对象
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "{entity_name}视图对象")
public class {entity_name}VO {{

    private String id;

{fields_str}
}}
'''


def generate_converter_methods(entity_name: str, fields: list[dict]) -> str:
    """生成 Converter 方法"""
    field_mappings = '\n'.join(
        f'        target.set{to_pascal(f["name"])}(source.get{to_pascal(f["name"])}());'
        for f in fields
    )

    return f'''    /**
     * PostDTO → Entity
     */
    default {entity_name} postDtoToEntity({entity_name}PostDTO dto) {{
        if (dto == null) {{
            return null;
        }}
        {entity_name} target = new {entity_name}();
{field_mappings}
        return target;
    }}

    /**
     * PutDTO → Entity（部分更新）
     */
    default {entity_name} putDtoToEntity({entity_name}PutDTO dto) {{
        if (dto == null) {{
            return null;
        }}
        {entity_name} target = new {entity_name}();
        target.setId(dto.getId());
{field_mappings}
        return target;
    }}

    /**
     * Entity → VO
     */
    default {entity_name}VO entityToVO({entity_name} entity) {{
        if (entity == null) {{
            return null;
        }}
        {entity_name}VO vo = new {entity_name}VO();
        vo.setId(entity.getId());
{field_mappings}
        return vo;
    }}
'''


def main():
    parser = argparse.ArgumentParser(description='YDSZ DTO/VO/Converter 代码生成器')
    parser.add_argument('--entity', required=True, help='Entity 类名（如 Department）')
    parser.add_argument('--package', required=True, help='模块包名（如 userinfo）')
    parser.add_argument('--output-dir', default='ydsz-backend',
                        help='输出根目录（默认: ydsz-backend）')
    args = parser.parse_args()

    entity_name = args.entity
    package = args.package
    output_root = pathlib.Path(args.output_dir)

    # 查找 Entity 文件
    entity_path = output_root / f'ydsz-{package}' / 'ydsz-{package}-domain' / \
        'src/main/java/com/njydsz' / package / 'domain/entity' / f'{entity_name}.java'

    print(f'查找 Entity: {entity_path}')
    fields = find_entity_fields(entity_path)

    if not fields:
        print(f'⚠️  未找到字段或 Entity 文件不存在: {entity_path}')
        print('   将生成空字段的 DTO/VO 模板')
    else:
        print(f'✅ 找到 {len(fields)} 个业务字段')

    # 生成 PostDTO
    post_dto = generate_post_dto(entity_name, package, fields)
    post_dto_path = output_root / f'ydsz-{package}' / 'ydsz-{package}-domain' / \
        'src/main/java/com/njydsz' / package / 'domain/dto/post' / f'{entity_name}PostDTO.java'
    post_dto_path.parent.mkdir(parents=True, exist_ok=True)
    post_dto_path.write_text(post_dto, encoding='utf-8')
    print(f'✅ 生成 PostDTO: {post_dto_path}')

    # 生成 PutDTO
    put_dto = generate_put_dto(entity_name, package, fields)
    put_dto_path = output_root / f'ydsz-{package}' / 'ydsz-{package}-domain' / \
        'src/main/java/com/njydsz' / package / 'domain/dto/put' / f'{entity_name}PutDTO.java'
    put_dto_path.parent.mkdir(parents=True, exist_ok=True)
    put_dto_path.write_text(put_dto, encoding='utf-8')
    print(f'✅ 生成 PutDTO: {put_dto_path}')

    # 生成 VO
    vo = generate_vo(entity_name, package, fields)
    vo_path = output_root / f'ydsz-{package}' / 'ydsz-{package}-domain' / \
        'src/main/java/com/njydsz' / package / 'domain/vo' / f'{entity_name}VO.java'
    vo_path.parent.mkdir(parents=True, exist_ok=True)
    vo_path.write_text(vo, encoding='utf-8')
    print(f'✅ 生成 VO: {vo_path}')

    # 生成 Converter 方法（输出到控制台，需手动粘贴到 Converter 接口中）
    converter_methods = generate_converter_methods(entity_name, fields)
    print(f'\n📋 Converter 方法（请粘贴到 {entity_name}Converter 接口中）:')
    print('=' * 60)
    print(converter_methods)
    print('=' * 60)


if __name__ == '__main__':
    main()
