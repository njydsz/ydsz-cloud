#!/usr/bin/env python3
"""
批量迁移 RedisTemplate → RedisService

将业务模块中直接使用 StringRedisTemplate/RedisTemplate 的代码
迁移为使用 ydsz-common-redis 提供的 RedisService 门面类。

迁移规则：
1. import org.springframework.data.redis.core.StringRedisTemplate
   → import com.njydsz.common.redis.service.RedisService
2. private final StringRedisTemplate redisTemplate;
   → private final RedisService redisService;
3. redisTemplate.opsForValue().get(key) → redisService.get(key, String.class)
4. redisTemplate.opsForValue().set(key, value) → redisService.set(key, value)
5. redisTemplate.opsForValue().set(key, value, duration) → redisService.set(key, value, duration)
6. redisTemplate.opsForValue().setIfAbsent(key, value, duration) → redisService.setIfAbsent(key, value, duration.toSeconds())
7. redisTemplate.opsForValue().increment(key) → redisService.incr(key, 1)
8. redisTemplate.delete(key) → redisService.delete(key)
9. redisTemplate.opsForHash().put(key, field, value) → redisService.hSet(key, field, value)
10. redisTemplate.opsForHash().get(key, field) → redisService.hGet(key, field, String.class)
11. redisTemplate.opsForHash().entries(key) → redisService.hGetAll(key, String.class)
12. redisTemplate.opsForSet().add(key, values...) → redisService.sAdd(key, values...)
13. redisTemplate.opsForSet().members(key) → redisService.sMembers(key, String.class)
14. redisTemplate.opsForSet().isMember(key, value) → redisService.sIsMember(key, value)
"""

import re
import sys
from pathlib import Path


def migrate_file(file_path: Path) -> bool:
    """迁移单个文件，返回是否修改"""
    try:
        content = file_path.read_text(encoding='utf-8')
    except Exception as e:
        print(f"❌ 读取失败: {file_path} - {e}", file=sys.stderr)
        return False

    original = content
    changed = False

    # 跳过已经使用 RedisService 的文件
    if 'RedisService' in content and 'StringRedisTemplate' not in content:
        return False

    # 1. 替换 import
    if 'import org.springframework.data.redis.core.StringRedisTemplate;' in content:
        content = content.replace(
            'import org.springframework.data.redis.core.StringRedisTemplate;',
            'import com.njydsz.common.redis.service.RedisService;'
        )
        changed = True

    if 'import org.springframework.data.redis.core.RedisTemplate;' in content:
        content = content.replace(
            'import org.springframework.data.redis.core.RedisTemplate;',
            'import com.njydsz.common.redis.service.RedisService;'
        )
        changed = True

    # 2. 替换字段声明
    # private final StringRedisTemplate redisTemplate;
    content = re.sub(
        r'private\s+final\s+StringRedisTemplate\s+(\w+)\s*;',
        r'private final RedisService \1;',
        content
    )
    # private final RedisTemplate<String, Object> redisTemplate;
    content = re.sub(
        r'private\s+final\s+RedisTemplate<[^>]+>\s+(\w+)\s*;',
        r'private final RedisService \1;',
        content
    )

    # 3. 替换方法调用
    # redisTemplate.opsForValue().get(key) → redisService.get(key, String.class)
    content = re.sub(
        r'(\w+)\.opsForValue\(\)\.get\(([^)]+)\)',
        r'\1.get(\2, String.class)',
        content
    )

    # redisTemplate.opsForValue().set(key, value) → redisService.set(key, value)
    content = re.sub(
        r'(\w+)\.opsForValue\(\)\.set\(([^,]+),\s*([^,)]+)\)',
        r'\1.set(\2, \3)',
        content
    )

    # redisTemplate.opsForValue().set(key, value, duration) → redisService.set(key, value, duration)
    # 保持 Duration 参数不变
    content = re.sub(
        r'(\w+)\.opsForValue\(\)\.set\(([^,]+),\s*([^,]+),\s*([^)]+)\)',
        r'\1.set(\2, \3, \4)',
        content
    )

    # redisTemplate.opsForValue().setIfAbsent(key, value, duration) → redisService.setIfAbsent(key, value, duration.toSeconds())
    def replace_set_if_absent(m):
        var_name = m.group(1)
        key = m.group(2)
        value = m.group(3)
        duration = m.group(4)
        # 如果 duration 是 Duration.ofXxx()，提取秒数
        if 'Duration.ofSeconds(' in duration:
            seconds = re.search(r'Duration\.ofSeconds\(([^)]+)\)', duration)
            if seconds:
                return f'{var_name}.setIfAbsent({key}, {value}, {seconds.group(1)})'
        elif 'Duration.ofMinutes(' in duration:
            minutes = re.search(r'Duration\.ofMinutes\(([^)]+)\)', duration)
            if minutes:
                return f'{var_name}.setIfAbsent({key}, {value}, {minutes.group(1)} * 60)'
        # 默认调用 toSeconds()
        return f'{var_name}.setIfAbsent({key}, {value}, {duration}.toSeconds())'

    content = re.sub(
        r'(\w+)\.opsForValue\(\)\.setIfAbsent\(([^,]+),\s*([^,]+),\s*([^)]+)\)',
        replace_set_if_absent,
        content
    )

    # redisTemplate.opsForValue().increment(key) → redisService.incr(key, 1)
    content = re.sub(
        r'(\w+)\.opsForValue\(\)\.increment\(([^)]+)\)',
        r'\1.incr(\2, 1)',
        content
    )

    # redisTemplate.delete(key) → redisService.delete(key)
    content = re.sub(
        r'(\w+)\.delete\(([^)]+)\)',
        r'\1.delete(\2)',
        content
    )

    # redisTemplate.opsForHash().put(key, field, value) → redisService.hSet(key, field, value)
    content = re.sub(
        r'(\w+)\.opsForHash\(\)\.put\(([^,]+),\s*([^,]+),\s*([^)]+)\)',
        r'\1.hSet(\2, \3, \4)',
        content
    )

    # redisTemplate.opsForHash().get(key, field) → redisService.hGet(key, field, String.class)
    content = re.sub(
        r'(\w+)\.opsForHash\(\)\.get\(([^,]+),\s*([^)]+)\)',
        r'\1.hGet(\2, \3, String.class)',
        content
    )

    # redisTemplate.opsForHash().entries(key) → redisService.hGetAll(key, String.class)
    content = re.sub(
        r'(\w+)\.opsForHash\(\)\.entries\(([^)]+)\)',
        r'\1.hGetAll(\2, String.class)',
        content
    )

    # redisTemplate.opsForSet().add(key, values...) → redisService.sAdd(key, values...)
    content = re.sub(
        r'(\w+)\.opsForSet\(\)\.add\(([^)]+)\)',
        r'\1.sAdd(\2)',
        content
    )

    # redisTemplate.opsForSet().members(key) → redisService.sMembers(key, String.class)
    content = re.sub(
        r'(\w+)\.opsForSet\(\)\.members\(([^)]+)\)',
        r'\1.sMembers(\2, String.class)',
        content
    )

    # redisTemplate.opsForSet().isMember(key, value) → redisService.sIsMember(key, value)
    content = re.sub(
        r'(\w+)\.opsForSet\(\)\.isMember\(([^,]+),\s*([^)]+)\)',
        r'\1.sIsMember(\2, \3)',
        content
    )

    # 4. 替换变量名（如果字段名从 redisTemplate 改为 redisService）
    # 这一步需要谨慎处理，只替换字段引用
    if 'private final RedisService redisTemplate;' in content:
        content = content.replace(
            'private final RedisService redisTemplate;',
            'private final RedisService redisService;'
        )
        # 替换所有 redisTemplate. 为 redisService.
        content = content.replace('redisTemplate.', 'redisService.')

    if 'private final RedisService stringRedisTemplate;' in content:
        content = content.replace(
            'private final RedisService stringRedisTemplate;',
            'private final RedisService redisService;'
        )
        content = content.replace('stringRedisTemplate.', 'redisService.')

    # 写回文件
    if content != original:
        try:
            file_path.write_text(content, encoding='utf-8')
            return True
        except Exception as e:
            print(f"❌ 写入失败: {file_path} - {e}", file=sys.stderr)
            return False

    return False


def main():
    """主函数"""
    backend_dir = Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend')

    # 要处理的业务模块（排除 ydsz-common）
    modules = [
        'ydsz-system', 'ydsz-userinfo', 'ydsz-workflow', 'ydsz-cronjob',
        'ydsz-message', 'ydsz-literule', 'ydsz-agent', 'ydsz-nextwiki'
    ]

    migrated = []
    failed = []

    for module in modules:
        module_dir = backend_dir / module
        if not module_dir.exists():
            print(f"⚠️  模块不存在: {module}")
            continue

        # 查找所有 Java 文件
        java_files = list(module_dir.rglob('src/main/java/**/*.java'))
        print(f"\n📦 处理模块: {module} ({len(java_files)} 个 Java 文件)")

        for java_file in java_files:
            # 跳过测试文件
            if 'test' in str(java_file):
                continue

            # 只处理包含 RedisTemplate 的文件
            try:
                content = java_file.read_text(encoding='utf-8')
                if 'StringRedisTemplate' not in content and 'RedisTemplate' not in content:
                    continue
            except Exception:
                continue

            if migrate_file(java_file):
                migrated.append(java_file)
                print(f"  ✅ 已迁移: {java_file.relative_to(backend_dir)}")
            else:
                # 检查是否真的需要迁移
                try:
                    content = java_file.read_text(encoding='utf-8')
                    if 'StringRedisTemplate' in content or 'RedisTemplate' in content:
                        failed.append(java_file)
                        print(f"  ⚠️  需要手动检查: {java_file.relative_to(backend_dir)}")
                except Exception:
                    pass

    print(f"\n{'='*80}")
    print(f"📊 迁移完成统计:")
    print(f"  ✅ 成功迁移: {len(migrated)} 个文件")
    print(f"  ⚠️  需要手动检查: {len(failed)} 个文件")

    if failed:
        print(f"\n⚠️  以下文件需要手动检查:")
        for f in failed:
            print(f"  - {f.relative_to(backend_dir)}")


if __name__ == '__main__':
    main()
