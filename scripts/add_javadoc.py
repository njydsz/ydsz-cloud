#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Automated Javadoc enhancement script for ydsz-backend Java files.

Follows internet big-tech company standards (Alibaba/Google Java Style):
- Every class/interface/enum MUST have class-level Javadoc
- Every public method SHOULD have Javadoc with @param/@return/@throws
- Important fields SHOULD have inline comments
- Javadoc language: Chinese

Usage:
    python add_javadoc.py --module ydsz-gateway
    python add_javadoc.py --module ydsz-system --dry-run
    python add_javadoc.py --file path/to/File.java
    python add_javadoc.py --all
"""

import os
import re
import sys
import json
import argparse
from collections import OrderedDict

BACKEND = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'ydsz-backend')

# ─── Java file classification ───────────────────────────────────────────────

CLASS_TYPE_PATTERNS = [
    # (pattern_in_path_or_name, class_type_chinese)
    (r'/controller/', 'REST API 控制器'),
    (r'/service/impl/', '服务实现类'),
    (r'/service/', '服务接口'),
    (r'/repository/impl/', '仓储实现类'),
    (r'/repository/', '仓储接口'),
    (r'/domain/entity/', '领域实体'),
    (r'/domain/dto/', '数据传输对象'),
    (r'/domain/vo/', '视图对象'),
    (r'/domain/event/', '领域事件'),
    (r'/domain/model/', '领域模型'),
    (r'/domain/valueobject/', '值对象'),
    (r'/config/', '配置类'),
    (r'/health/', '健康检查组件'),
    (r'/metrics/', '监控指标组件'),
    (r'/filter/', '过滤器'),
    (r'/interceptor/', '拦截器'),
    (r'/aspect/', '切面'),
    (r'/listener/', '事件监听器'),
    (r'/handler/', '处理器'),
    (r'/adapter/', '适配器'),
    (r'/converter/', '转换器'),
    (r'/assembler/', '装配器'),
    (r'/strategy/', '策略'),
    (r'/factory/', '工厂'),
    (r'/builder/', '构建器'),
    (r'/gateway/', '网关组件'),
    (r'/mapper/', 'MyBatis Mapper'),
    (r'/client/', 'Feign 客户端'),
    (r'/fallback/', 'Feign 降级'),
    (r'/annotation/', '注解定义'),
    (r'/enum/', '枚举'),
    (r'/enums/', '枚举'),
    (r'/constant/', '常量定义'),
    (r'/constants/', '常量定义'),
    (r'/util/', '工具类'),
    (r'/utils/', '工具类'),
    (r'/exception/', '异常定义'),
    (r'/security/', '安全组件'),
    (r'/guardrail/', '安全护栏'),
    (r'/prompt/', '提示词模板'),
    (r'/rag/', 'RAG 检索增强'),
    (r'/tool/', 'Agent 工具'),
    (r'/trace/', '链路追踪'),
    (r'/memory/', '会话记忆'),
    (r'/llm/', 'LLM 客户端'),
    (r'/chat/', '聊天服务'),
    (r'/agent/', 'Agent 执行器'),
]

# Annotations that indicate class purpose
ANN_CONTROLLER = ['@RestController', '@Controller']
ANN_SERVICE = ['@Service']
ANN_COMPONENT = ['@Component']
ANN_CONFIGURATION = ['@Configuration', '@ConfigurationProperties']
ANN_BEAN = ['@Bean']
ANN_REPOSITORY = ['@Repository', '@Mapper']
ANN_ENTITY = ['@TableName', '@Entity']
ANN_FEIGN = ['@FeignClient']
ANN_ASPECT = ['@Aspect']


def classify_file(rel_path, class_name, annotations):
    """Classify a Java file based on path, name, and annotations."""
    # Check path patterns first
    for pattern, label in CLASS_TYPE_PATTERNS:
        if re.search(pattern, rel_path, re.IGNORECASE):
            return label

    # Check annotations
    for ann in ANN_CONTROLLER:
        if ann in annotations:
            return 'REST API 控制器'
    for ann in ANN_SERVICE:
        if ann in annotations:
            return '服务实现类'
    for ann in ANN_CONFIGURATION:
        if ann in annotations:
            return '配置类'
    for ann in ANN_ENTITY:
        if ann in annotations:
            return '数据库实体'
    for ann in ANN_FEIGN:
        if ann in annotations:
            return 'Feign 远程调用客户端'
    for ann in ANN_REPOSITORY:
        if ann in annotations:
            return '数据访问层'
    for ann in ANN_ASPECT:
        if ann in annotations:
            return 'AOP 切面'
    for ann in ANN_COMPONENT:
        if ann in annotations:
            return 'Spring 组件'

    # Check by class name suffix
    if class_name.endswith('Controller'):
        return 'REST API 控制器'
    if class_name.endswith('ServiceImpl'):
        return '服务实现类'
    if class_name.endswith('Service'):
        return '服务接口'
    if class_name.endswith('Repository'):
        return '仓储接口'
    if class_name.endswith('RepositoryImpl'):
        return '仓储实现类'
    if class_name.endswith('Mapper'):
        return 'MyBatis Mapper'
    if class_name.endswith('DTO') or class_name.endswith('Dto'):
        return '数据传输对象'
    if class_name.endswith('VO') or class_name.endswith('Vo'):
        return '视图对象'
    if class_name.endswith('DO') or class_name.endswith('Do'):
        return '数据库实体'
    if class_name.endswith('Config') or class_name.endswith('Configuration'):
        return '配置类'
    if class_name.endswith('Properties'):
        return '配置属性'
    if class_name.endswith('HealthIndicator'):
        return '健康检查组件'
    if class_name.endswith('Metrics'):
        return '监控指标组件'
    if class_name.endswith('Filter'):
        return '过滤器'
    if class_name.endswith('Interceptor'):
        return '拦截器'
    if class_name.endswith('Aspect'):
        return 'AOP 切面'
    if class_name.endswith('Listener'):
        return '事件监听器'
    if class_name.endswith('Handler'):
        return '处理器'
    if class_name.endswith('Exception'):
        return '异常定义'
    if class_name.endswith('Enum'):
        return '枚举'
    if class_name.endswith('Util') or class_name.endswith('Utils'):
        return '工具类'
    if class_name.endswith('Constant') or class_name.endswith('Constants'):
        return '常量定义'
    if class_name.endswith('Application'):
        return 'Spring Boot 启动类'
    if class_name.endswith('Client'):
        return '远程调用客户端'
    if class_name.endswith('Fallback'):
        return '降级处理'
    if class_name.endswith('Strategy'):
        return '策略'
    if class_name.endswith('Factory'):
        return '工厂'
    if class_name.endswith('Builder'):
        return '构建器'
    if class_name.endswith('Adapter'):
        return '适配器'
    if class_name.endswith('Converter'):
        return '转换器'

    return 'Java 类'


# ─── Java file parsing ─────────────────────────────────────────────────────

def parse_java_file(content):
    """Parse Java file content and extract key information."""
    info = {
        'package': '',
        'imports': [],
        'class_name': '',
        'class_type': 'class',  # class, interface, enum, @interface
        'annotations': [],
        'has_class_javadoc': False,
        'class_decl_line': -1,
        'javadoc_end_line': -1,  # line where existing Javadoc ends (if any)
        'lines': content.split('\n'),
    }

    # Extract package
    pkg_match = re.search(r'^package\s+([\w.]+);', content, re.MULTILINE)
    if pkg_match:
        info['package'] = pkg_match.group(1)

    # Find class/interface/enum declaration
    # Skip import statements and look for class declaration
    class_pattern = r'(?:@\w+(?:\([^)]*\))?\s*)*(public\s+|protected\s+)?(abstract\s+)?(final\s+)?(class|interface|enum|@interface)\s+(\w+)'
    
    lines = info['lines']
    in_block_comment = False
    in_javadoc = False
    javadoc_start = -1
    
    for i, line in enumerate(lines):
        stripped = line.strip()
        
        # Track block comments
        if in_block_comment:
            if '*/' in stripped:
                in_block_comment = False
            continue
        
        if in_javadoc:
            if '*/' in stripped:
                in_javadoc = False
                info['javadoc_end_line'] = i
                # Check if the next non-empty line is a class declaration
                for j in range(i + 1, min(i + 5, len(lines))):
                    next_stripped = lines[j].strip()
                    if not next_stripped or next_stripped.startswith('@'):
                        continue
                    if re.match(r'(public\s+|protected\s+)?(abstract\s+)?(final\s+)?(class|interface|enum|@interface)\s+', next_stripped):
                        info['has_class_javadoc'] = True
                        info['class_decl_line'] = j
                        m = re.search(r'(class|interface|enum|@interface)\s+(\w+)', next_stripped)
                        if m:
                            info['class_type'] = m.group(1)
                            info['class_name'] = m.group(2)
                        break
                    else:
                        break
            continue

        if stripped.startswith('/**'):
            in_javadoc = True
            javadoc_start = i
            if '*/' in stripped and not stripped.endswith('*/'):
                pass  # single line javadoc
            elif '*/' in stripped:
                in_javadoc = False
                info['javadoc_end_line'] = i
                # Check next line
                for j in range(i + 1, min(i + 5, len(lines))):
                    next_stripped = lines[j].strip()
                    if not next_stripped or next_stripped.startswith('@'):
                        continue
                    if re.match(r'(public\s+|protected\s+)?(abstract\s+)?(final\s+)?(class|interface|enum|@interface)\s+', next_stripped):
                        info['has_class_javadoc'] = True
                        info['class_decl_line'] = j
                        m = re.search(r'(class|interface|enum|@interface)\s+(\w+)', next_stripped)
                        if m:
                            info['class_type'] = m.group(1)
                            info['class_name'] = m.group(2)
                        break
                    else:
                        break
            continue

        if stripped.startswith('/*') and not stripped.startswith('/**'):
            in_block_comment = True
            if '*/' in stripped:
                in_block_comment = False
            continue

        # Look for class declaration (possibly preceded by annotations)
        # Collect annotations
        if stripped.startswith('@') and not stripped.startswith('@interface'):
            ann_match = re.match(r'@(\w+)', stripped)
            if ann_match:
                ann_name = '@' + ann_match.group(1)
                # Collect multi-line annotations
                if '(' in stripped and ')' not in stripped:
                    # Multi-line annotation, collect until closing paren
                    full_ann = stripped
                    j = i + 1
                    while j < len(lines) and ')' not in full_ann:
                        full_ann += ' ' + lines[j].strip()
                        j += 1
                    info['annotations'].append(ann_name)
                else:
                    info['annotations'].append(ann_name)
            continue

        # Check for class declaration
        class_match = re.match(
            r'(public\s+|protected\s+)?(abstract\s+)?(final\s+)?(class|interface|enum|@interface)\s+(\w+)',
            stripped
        )
        if class_match and not info['class_name']:
            info['class_type'] = class_match.group(4)
            info['class_name'] = class_match.group(5)
            info['class_decl_line'] = i
            # Check if there was a Javadoc just before
            if not info['has_class_javadoc'] and info['javadoc_end_line'] >= 0:
                # Check if there are only annotations or blank lines between Javadoc and class
                has_only_annotations = True
                for j in range(info['javadoc_end_line'] + 1, i):
                    s = lines[j].strip()
                    if s and not s.startswith('@'):
                        has_only_annotations = False
                        break
                if has_only_annotations:
                    info['has_class_javadoc'] = True
            break

    return info


def generate_class_javadoc(class_name, class_type, class_type_cn, annotations, package, rel_path):
    """Generate appropriate class-level Javadoc."""
    # Determine specific description based on class type and name
    desc = class_type_cn

    # More specific descriptions based on class name patterns
    if class_name.endswith('Controller'):
        # Try to determine the domain from class name
        domain = class_name.replace('Controller', '')
        desc = f'{domain} REST API 控制器，提供 {domain} 相关的 HTTP 接口'
    elif class_name.endswith('ServiceImpl'):
        domain = class_name.replace('ServiceImpl', '')
        desc = f'{domain} 服务实现类，封装 {domain} 核心业务逻辑'
    elif class_name.endswith('Service') and not class_name.endswith('ServiceImpl'):
        domain = class_name.replace('Service', '')
        desc = f'{domain} 服务接口，定义 {domain} 业务操作契约'
    elif class_name.endswith('RepositoryImpl'):
        domain = class_name.replace('RepositoryImpl', '')
        desc = f'{domain} 仓储实现类，提供 {domain} 数据持久化能力'
    elif class_name.endswith('Repository'):
        domain = class_name.replace('Repository', '')
        desc = f'{domain} 仓储接口，定义 {domain} 数据访问契约'
    elif class_name.endswith('Mapper'):
        domain = class_name.replace('Mapper', '')
        desc = f'{domain} MyBatis Mapper，提供 {domain} 数据库操作'
    elif class_name.endswith('DTO') or class_name.endswith('Dto'):
        domain = class_name.replace('DTO', '').replace('Dto', '')
        desc = f'{domain} 数据传输对象，用于服务间数据传递'
    elif class_name.endswith('VO') or class_name.endswith('Vo'):
        domain = class_name.replace('VO', '').replace('Vo', '')
        desc = f'{domain} 视图对象，用于 API 响应数据封装'
    elif class_name.endswith('DO') or class_name.endswith('Do'):
        domain = class_name.replace('DO', '').replace('Do', '')
        desc = f'{domain} 数据库实体，映射 {domain} 数据库表'
    elif class_name.endswith('Config') or class_name.endswith('Configuration'):
        domain = class_name.replace('Configuration', '').replace('Config', '')
        desc = f'{domain} 配置类，提供 {domain} 相关 Bean 定义和配置'
    elif class_name.endswith('Properties'):
        domain = class_name.replace('Properties', '')
        desc = f'{domain} 配置属性类，绑定 {domain} 相关配置项'
    elif class_name.endswith('HealthIndicator'):
        domain = class_name.replace('HealthIndicator', '')
        desc = f'{domain} 健康检查组件，监控 {domain} 服务运行状态'
    elif class_name.endswith('Metrics'):
        domain = class_name.replace('Metrics', '')
        desc = f'{domain} 监控指标组件，采集和暴露 {domain} 相关 Micrometer 指标'
    elif class_name.endswith('Filter'):
        domain = class_name.replace('Filter', '')
        desc = f'{domain} 过滤器，在请求处理链中执行 {domain} 相关过滤逻辑'
    elif class_name.endswith('Interceptor'):
        domain = class_name.replace('Interceptor', '')
        desc = f'{domain} 拦截器，在方法调用前后执行 {domain} 相关拦截逻辑'
    elif class_name.endswith('Aspect'):
        domain = class_name.replace('Aspect', '')
        desc = f'{domain} AOP 切面，通过切面编程实现 {domain} 横切关注点'
    elif class_name.endswith('Listener'):
        domain = class_name.replace('Listener', '')
        desc = f'{domain} 事件监听器，处理 {domain} 相关领域事件'
    elif class_name.endswith('Handler'):
        domain = class_name.replace('Handler', '')
        desc = f'{domain} 处理器，负责 {domain} 相关请求或消息处理'
    elif class_name.endswith('Exception'):
        domain = class_name.replace('Exception', '')
        desc = f'{domain} 异常类，表示 {domain} 相关业务异常'
    elif class_name.endswith('Enum'):
        domain = class_name.replace('Enum', '')
        desc = f'{domain} 枚举，定义 {domain} 相关枚举值'
    elif class_name.endswith('Application'):
        domain = class_name.replace('Application', '')
        desc = f'{domain} Spring Boot 应用启动类'
    elif class_name.endswith('Client'):
        domain = class_name.replace('Client', '')
        desc = f'{domain} Feign 远程调用客户端'
    elif class_name.endswith('Fallback'):
        domain = class_name.replace('Fallback', '')
        desc = f'{domain} Feign 降级处理，在远程调用失败时提供兜底逻辑'
    elif class_name.endswith('Strategy'):
        domain = class_name.replace('Strategy', '')
        desc = f'{domain} 策略实现，提供 {domain} 相关算法或行为'
    elif class_name.endswith('Factory'):
        domain = class_name.replace('Factory', '')
        desc = f'{domain} 工厂类，负责 {domain} 对象创建'
    elif class_name.endswith('Builder'):
        domain = class_name.replace('Builder', '')
        desc = f'{domain} 构建器，提供 {domain} 对象的链式构建能力'
    elif class_name.endswith('Adapter'):
        domain = class_name.replace('Adapter', '')
        desc = f'{domain} 适配器，适配 {domain} 相关接口'
    elif class_name.endswith('Converter'):
        domain = class_name.replace('Converter', '')
        desc = f'{domain} 转换器，提供 {domain} 对象类型转换'
    elif class_name.endswith('Util') or class_name.endswith('Utils'):
        domain = class_name.replace('Utils', '').replace('Util', '')
        desc = f'{domain} 工具类，提供 {domain} 相关静态工具方法'
    elif class_name.endswith('Constant') or class_name.endswith('Constants'):
        domain = class_name.replace('Constants', '').replace('Constant', '')
        desc = f'{domain} 常量定义类'
    elif class_type == 'enum':
        desc = f'{class_name} 枚举，定义相关枚举值'
    elif class_type == 'interface':
        desc = f'{class_name} 接口，定义相关操作契约'
    elif class_type == '@interface':
        desc = f'{class_name} 注解定义'

    # Build Javadoc
    module_name = package.split('.')[-2] if len(package.split('.')) >= 2 else ''
    
    javadoc_lines = [
        '/**',
        f' * {desc}。',
        ' *',
        ' * <p>模块: {module}</p>'.format(module=module_name or package),
    ]

    # Add author info if not present in file
    javadoc_lines.append(' *')
    javadoc_lines.append(' * @author ydsz-pmis-team')
    javadoc_lines.append(' * @since 1.0.0')
    javadoc_lines.append(' */')

    return '\n'.join(javadoc_lines)


def insert_class_javadoc(content, javadoc, info):
    """Insert class-level Javadoc before the class declaration."""
    lines = content.split('\n')
    decl_line = info['class_decl_line']
    
    if decl_line < 0:
        return content

    # If there's existing Javadoc, replace it
    if info['has_class_javadoc'] and info['javadoc_end_line'] >= 0:
        # Find the start of the existing Javadoc
        javadoc_end = info['javadoc_end_line']
        javadoc_start = javadoc_end
        for i in range(javadoc_end, -1, -1):
            if '/**' in lines[i]:
                javadoc_start = i
                break
        
        # Check if there's existing @author/@since we should preserve
        existing_javadoc = '\n'.join(lines[javadoc_start:javadoc_end+1])
        has_author = '@author' in existing_javadoc
        has_since = '@since' in existing_javadoc
        
        if has_author and has_since:
            # Keep existing Javadoc, don't overwrite
            return content
        
        # Remove existing Javadoc and annotations between it and class
        # Find the line after Javadoc end
        insert_point = javadoc_end + 1
        
        # Collect annotation lines between Javadoc and class declaration
        annotation_lines = []
        for i in range(insert_point, decl_line):
            s = lines[i].strip()
            if s.startswith('@'):
                annotation_lines.append(lines[i])
            elif not s:
                continue
            else:
                break
        
        # Remove old Javadoc
        new_lines = lines[:javadoc_start]
        # Add new Javadoc
        new_lines.extend(javadoc.split('\n'))
        # Add annotations
        new_lines.extend(annotation_lines)
        # Add the rest from class declaration onwards
        new_lines.extend(lines[decl_line:])
        
        return '\n'.join(new_lines)
    else:
        # No existing Javadoc, insert before annotations/class declaration
        # Find the first annotation or class declaration line
        insert_point = decl_line
        
        # Look backwards for annotations
        for i in range(decl_line - 1, -1, -1):
            s = lines[i].strip()
            if s.startswith('@'):
                insert_point = i
            elif not s:
                continue
            else:
                break
        
        # Collect annotations
        annotation_lines = lines[insert_point:decl_line]
        
        new_lines = lines[:insert_point]
        new_lines.extend(javadoc.split('\n'))
        new_lines.extend(annotation_lines)
        new_lines.extend(lines[decl_line:])
        
        return '\n'.join(new_lines)


def process_file(filepath, rel_path, dry_run=False):
    """Process a single Java file: add class-level Javadoc if missing."""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        return False, f'Read error: {e}'

    info = parse_java_file(content)
    
    if not info['class_name']:
        return False, 'Could not parse class declaration'
    
    if info['has_class_javadoc']:
        # Check if existing Javadoc has @author and @since
        if info['javadoc_end_line'] >= 0:
            lines = content.split('\n')
            javadoc_start = info['javadoc_end_line']
            for i in range(info['javadoc_end_line'], -1, -1):
                if '/**' in lines[i]:
                    javadoc_start = i
                    break
            existing = '\n'.join(lines[javadoc_start:info['javadoc_end_line']+1])
            if '@author' in existing and '@since' in existing:
                return False, 'Already has complete Javadoc'
    
    class_type_cn = classify_file(rel_path, info['class_name'], info['annotations'])
    javadoc = generate_class_javadoc(
        info['class_name'], info['class_type'], class_type_cn,
        info['annotations'], info['package'], rel_path
    )
    
    new_content = insert_class_javadoc(content, javadoc, info)
    
    if new_content == content:
        return False, 'No changes made'
    
    if not dry_run:
        try:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(new_content)
        except Exception as e:
            return False, f'Write error: {e}'
    
    return True, f'Added class Javadoc ({class_type_cn})'


def main():
    parser = argparse.ArgumentParser(description='Add class-level Javadoc to Java files')
    parser.add_argument('--module', help='Process specific module (e.g., ydsz-gateway)')
    parser.add_argument('--file', help='Process specific file')
    parser.add_argument('--all', action='store_true', help='Process all modules')
    parser.add_argument('--dry-run', action='store_true', help='Don\'t write changes, just report')
    args = parser.parse_args()
    
    # Load needs_work list
    json_path = os.path.join(os.path.dirname(BACKEND), 'scripts', 'needs_work_files.json')
    with open(json_path, 'r', encoding='utf-8') as f:
        needs_work = json.load(f)
    
    if args.file:
        filepath = args.file
        if not os.path.isabs(filepath):
            filepath = os.path.join(BACKEND, filepath)
        rel = os.path.relpath(filepath, BACKEND).replace(os.sep, '/')
        ok, msg = process_file(filepath, rel, args.dry_run)
        status = 'OK' if ok else 'SKIP'
        print(f'[{status}] {rel}: {msg}')
        return
    
    # Determine target files
    if args.all:
        targets = needs_work
    elif args.module:
        targets = [f for f in needs_work if f['module'] == args.module]
    else:
        parser.print_help()
        return
    
    # Group by submodule for reporting
    success_count = 0
    skip_count = 0
    error_count = 0
    
    for entry in targets:
        rel = entry['file']
        filepath = os.path.join(BACKEND, rel.replace('/', os.sep))
        
        if not os.path.exists(filepath):
            print(f'[ERR] {rel}: File not found')
            error_count += 1
            continue
        
        ok, msg = process_file(filepath, rel, args.dry_run)
        if ok:
            success_count += 1
            if args.dry_run:
                print(f'[DRY] {rel}: {msg}')
        else:
            skip_count += 1
    
    print(f'\n=== Summary ===')
    print(f'Total: {len(targets)}')
    print(f'Success: {success_count}')
    print(f'Skipped: {skip_count}')
    print(f'Error: {error_count}')


if __name__ == '__main__':
    main()
