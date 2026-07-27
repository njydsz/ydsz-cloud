"""
批量添加 @Idempotent 注解到所有缺少的 Controller 文件。
针对已有 @SentinelRateLimit 但缺少 @Idempotent 的 POST/PUT/DELETE 方法。
"""
import pathlib
import re

BACKEND_DIR = pathlib.Path("d:/Code/ydsz/ydsz-pmis/ydsz-backend")

# 模块名 -> 模块 key 前缀映射
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
    """从文件路径提取模块名"""
    parts = file_path.parts
    for part in parts:
        if part in MODULE_KEY_MAP:
            return part
    return "unknown"


def get_controller_name(file_path: pathlib.Path) -> str:
    """从文件路径提取 Controller 类名"""
    return file_path.stem  # 去掉 .java 后缀


def get_module_key_prefix(module_name: str) -> str:
    return MODULE_KEY_MAP.get(module_name, "ydsz:unknown")


def process_file(file_path: pathlib.Path) -> bool:
    """处理单个文件，返回是否修改"""
    module_name = extract_module_name(file_path)
    controller_name = get_controller_name(file_path)
    key_prefix = get_module_key_prefix(module_name)

    content = file_path.read_text(encoding="utf-8")

    # 检查是否已有 @Idempotent import
    has_idempotent_import = "import com.njydsz.common.lock.annotation.Idempotent" in content

    modified = False

    # 匹配模式：@SentinelRateLimit(...) 后紧跟 @PostMapping/@PutMapping/@DeleteMapping 的方法
    # 这些方法缺少 @Idempotent
    pattern = re.compile(
        r'((    )@SentinelRateLimit\([^)]+\)\n'
        r'\2@(?:Post|Put|Delete)Mapping[^\n]*\n'
        r'(?:\2@\w+[^\n]*\n)*'
        r'\2public\s+\S+\s+(\w+)\s*\()',
        re.MULTILINE
    )

    def replacement(match):
        full_match = match.group(0)
        indent = match.group(1)
        method_name = match.group(3) if match.lastindex and match.lastindex >= 3 else match.group(2)

        # 构建 @Idempotent 注解
        key = f"{key_prefix}:{controller_name}:{method_name}:lock"
        idempotent_annotation = f'{indent}@Idempotent(key = "{key}", ttlSeconds = 5)\n'

        # 插入在 @SentinelRateLimit 之前
        return idempotent_annotation + full_match

    new_content = pattern.sub(replacement, content)

    if new_content != content:
        # 添加 import 语句
        if not has_idempotent_import:
            # 在最后一个 import 之后添加
            import_pattern = re.compile(r'(import\s+[^;]+;\n)(?!import)')
            last_import_match = None
            for m in import_pattern.finditer(new_content):
                last_import_match = m
            if last_import_match:
                pos = last_import_match.end()
                new_content = new_content[:pos] + IDEMPOTENT_IMPORT + "\n" + new_content[pos:]
            else:
                # 没有 import，在 package 之后添加
                pkg_end = new_content.find(";\n") + 2
                if pkg_end > 1:
                    new_content = new_content[:pkg_end] + "\n" + IDEMPOTENT_IMPORT + "\n" + new_content[pkg_end:]

        file_path.write_text(new_content, encoding="utf-8")
        modified = True

    return modified


def main():
    total = 0
    modified_count = 0
    skipped = []

    for module_dir in BACKEND_DIR.iterdir():
        if not module_dir.is_dir():
            continue
        module_name = module_dir.name
        if module_name not in MODULE_KEY_MAP:
            continue

        web_dir = module_dir / f"{module_name}-web"
        if not web_dir.exists():
            continue

        for java_file in web_dir.rglob("**/controller/**/*Controller.java"):
            total += 1
            try:
                if process_file(java_file):
                    modified_count += 1
                    print(f"  ✓ {java_file.relative_to(BACKEND_DIR)}")
                else:
                    skipped.append(str(java_file.relative_to(BACKEND_DIR)))
            except Exception as e:
                print(f"  ✗ {java_file.relative_to(BACKEND_DIR)}: {e}")

    print(f"\n总计: {total} 个 Controller 文件")
    print(f"已修改: {modified_count} 个")
    print(f"无需修改: {len(skipped)} 个")

    if skipped:
        print("\n无需修改的 Controller:")
        for s in skipped:
            print(f"  - {s}")


if __name__ == "__main__":
    main()