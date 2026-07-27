"""
综合修复脚本：
1. 移除重复的 @RateLimit 注解
2. 统一 @Idempotent key 格式为 ydsz:{module}:{Controller}:{method}:lock
3. 为有 @RateLimit 但缺少 @Idempotent 的 POST/PUT/DELETE 方法添加 @Idempotent
"""
import pathlib
import re

BACKEND_DIR = pathlib.Path("d:/Code/ydsz/ydsz-pmis/ydsz-backend")

MODULE_KEY_MAP = {
    "ydsz-userinfo": "ydsz:userinfo",
    "ydsz-workflow": "ydsz:workflow",
    "ydsz-nextwiki": "ydsz:nextwiki",
    "ydsz-message": "ydsz:message",
    "ydsz-cronjob": "ydsz:cronjob",
    "ydsz-agent": "ydsz:agent",
    "ydsz-literule": "ydsz:literule",
    "ydsz-system": "ydsz:system",
    "ydsz-project": "ydsz:project",
}

IDEMPOTENT_IMPORT = "import com.njydsz.common.lock.annotation.Idempotent;"


def extract_module_name(file_path: pathlib.Path) -> str:
    parts = file_path.parts
    for part in parts:
        if part in MODULE_KEY_MAP:
            return part
    return "unknown"


def get_controller_name(file_path: pathlib.Path) -> str:
    return file_path.stem


def get_module_key_prefix(module_name: str) -> str:
    return MODULE_KEY_MAP.get(module_name, "ydsz:unknown")


def fix_duplicate_sentinel(content: str) -> str:
    """移除重复的 @RateLimit 注解（连续两行相同）"""
    pattern = re.compile(
        r'(@RateLimit\([^)]+\))\n(\s+)\1\n',
        re.MULTILINE
    )
    # 不断替换直到没有重复
    prev = None
    while prev != content:
        prev = content
        content = pattern.sub(r'\1\n', content)
    return content


def fix_idempotent_key(content: str, file_path: pathlib.Path) -> str:
    """统一 @Idempotent key 格式，并修正错误的 key"""
    module_name = extract_module_name(file_path)
    controller_name = get_controller_name(file_path)
    key_prefix = get_module_key_prefix(module_name)

    def replace_key(match):
        indent = match.group(1)
        key = match.group(2)
        # 提取 method 名称（从 key 中提取，或使用默认值）
        # 旧的 key 格式可能是 "department:create" 或 "ydsz:userinfo:DepartmentController:create:lock"
        parts = key.split(":")
        if len(parts) >= 2:
            method_name = parts[-2] if parts[-1] == "lock" else parts[-1]
        else:
            method_name = key

        # 构建标准 key
        new_key = f'{key_prefix}:{controller_name}:{method_name}:lock'
        return f'{indent}@Idempotent(key = "{new_key}", ttlSeconds = 5)'

    # 匹配 @Idempotent(key = "...", ttlSeconds = ...) 或 @Idempotent(key = "...", ...)
    pattern = re.compile(
        r'(\s+)@Idempotent\(key\s*=\s*"([^"]+)"[^)]*\)',
        re.MULTILINE
    )
    return pattern.sub(replace_key, content)


def add_missing_idempotent(content: str, file_path: pathlib.Path) -> str:
    """为有 @RateLimit 但缺少 @Idempotent 的 POST/PUT/DELETE 方法添加 @Idempotent"""
    module_name = extract_module_name(file_path)
    controller_name = get_controller_name(file_path)
    key_prefix = get_module_key_prefix(module_name)

    # 匹配模式：@RateLimit(...) 后出现 @PostMapping/@PutMapping/@DeleteMapping
    # 中间可能有其他注解（@Audit 等），但不能有 @Idempotent
    # 方法体以 public ... methodName( 开头
    pattern = re.compile(
        r'((    )@RateLimit\([^)]+\)\n)'
        r'((?:\2@(?!Idempotent)\w+[^\n]*\n)*)'
        r'(\2@(?:Post|Put|Delete)Mapping[^\n]*\n)'
        r'((?:\2@\w+[^\n]*\n)*)'
        r'(\2public\s+\S+\s+(\w+)\s*\()',
        re.MULTILINE
    )

    def replacement(match):
        sentinel_line = match.group(1)  # includes the @RateLimit line
        mid_annotations = match.group(3)  # annotations between RateLimit and the mapping
        mapping_line = match.group(4)  # @PostMapping/@PutMapping/@DeleteMapping
        post_annotations = match.group(5)  # annotations after mapping but before public
        method_sig = match.group(6)  # public ... methodName(
        method_name = match.group(7)  # methodName

        indent = match.group(2)

        # Build @Idempotent
        new_key = f'{key_prefix}:{controller_name}:{method_name}:lock'
        idempotent_line = f'{indent}@Idempotent(key = "{new_key}", ttlSeconds = 5)\n'

        return sentinel_line + idempotent_line + mid_annotations + mapping_line + post_annotations + method_sig

    return pattern.sub(replacement, content)


def add_import_if_needed(content: str) -> str:
    """如果内容中有 @Idempotent 但没有 import，则添加 import"""
    if "@Idempotent" not in content:
        return content
    if IDEMPOTENT_IMPORT in content:
        return content

    # 在最后一个 import 之后添加
    lines = content.split("\n")
    last_import_idx = -1
    for i, line in enumerate(lines):
        if line.strip().startswith("import "):
            last_import_idx = i

    if last_import_idx >= 0:
        lines.insert(last_import_idx + 1, IDEMPOTENT_IMPORT)
    else:
        # 没有 import，在 package 行之后添加
        for i, line in enumerate(lines):
            if line.strip().startswith("package "):
                lines.insert(i + 1, "")
                lines.insert(i + 2, IDEMPOTENT_IMPORT)
                break

    return "\n".join(lines)


def process_file(file_path: pathlib.Path) -> tuple:
    """处理单个文件，返回 (modified, reason)"""
    content = file_path.read_text(encoding="utf-8")
    original = content

    # Step 1: 移除重复的 @RateLimit
    content = fix_duplicate_sentinel(content)

    # Step 2: 统一 @Idempotent key 格式
    content = fix_idempotent_key(content, file_path)

    # Step 3: 添加缺失的 @Idempotent
    content = add_missing_idempotent(content, file_path)

    # Step 4: 添加 import
    content = add_import_if_needed(content)

    if content != original:
        file_path.write_text(content, encoding="utf-8")
        return True, "modified"
    return False, "unchanged"


def main():
    total = 0
    modified = 0
    unchanged = 0

    for module_dir in sorted(BACKEND_DIR.iterdir()):
        if not module_dir.is_dir():
            continue
        module_name = module_dir.name
        if module_name not in MODULE_KEY_MAP:
            continue

        web_dir = module_dir / f"{module_name}-web"
        if not web_dir.exists():
            continue

        for java_file in sorted(web_dir.rglob("**/controller/**/*Controller.java")):
            total += 1
            try:
                changed, reason = process_file(java_file)
                if changed:
                    modified += 1
                    print(f"  ✓ {java_file.relative_to(BACKEND_DIR)}")
                else:
                    unchanged += 1
            except Exception as e:
                print(f"  ✗ {java_file.relative_to(BACKEND_DIR)}: {e}")

    print(f"\n总计: {total} 个 Controller 文件")
    print(f"已修改: {modified} 个")
    print(f"无需修改: {unchanged} 个")


if __name__ == "__main__":
    main()