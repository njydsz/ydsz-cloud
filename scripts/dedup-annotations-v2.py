"""
精确去重：在每段注解块（两个 public 方法签名之间）内，移除重复的 @RateLimit 和 @Idempotent。
处理跨多行的注解（如 @Audit 分两行）。
"""
import pathlib
import re

BACKEND_DIR = pathlib.Path("d:/Code/ydsz/ydsz-pmis/ydsz-backend")

MODULE_KEY_MAP = {
    "ydsz-userinfo", "ydsz-workflow", "ydsz-nextwiki", "ydsz-message",
    "ydsz-cronjob", "ydsz-agent", "ydsz-literule", "ydsz-system", "ydsz-project",
}


def dedup_in_annotation_block(content: str) -> str:
    """在方法级别去重注解"""
    # 找到所有方法签名位置（public/private/protected 后跟返回类型和方法名）
    method_pattern = re.compile(r'^(\s+)(public|private|protected)\s+\S+\s+\w+\s*\(', re.MULTILINE)

    # 找到所有方法签名的起始位置
    method_positions = [m.start() for m in method_pattern.finditer(content)]

    if not method_positions:
        return content

    result_parts = []
    prev_pos = 0

    for pos in method_positions:
        block = content[prev_pos:pos]
        # 去重这个注解块
        deduped = dedup_block(block)
        result_parts.append(deduped)
        prev_pos = pos

    # 最后一段
    result_parts.append(content[prev_pos:])

    return "".join(result_parts)


def dedup_block(block: str) -> str:
    """在单个注解块中去重 @RateLimit 和 @Idempotent"""
    lines = block.split("\n")
    result = []
    seen_sentinel = set()
    seen_idempotent = set()

    for line in lines:
        stripped = line.strip()

        if stripped.startswith("@RateLimit"):
            if stripped in seen_sentinel:
                continue
            seen_sentinel.add(stripped)
        elif stripped.startswith("@Idempotent"):
            if stripped in seen_idempotent:
                continue
            seen_idempotent.add(stripped)

        result.append(line)

    return "\n".join(result)


def process_file(file_path: pathlib.Path) -> bool:
    content = file_path.read_text(encoding="utf-8")
    new_content = dedup_in_annotation_block(content)
    if new_content != content:
        file_path.write_text(new_content, encoding="utf-8")
        return True
    return False


def main():
    total = 0
    modified = 0

    for module_dir in sorted(BACKEND_DIR.iterdir()):
        if not module_dir.is_dir():
            continue
        if module_dir.name not in MODULE_KEY_MAP:
            continue

        web_dir = module_dir / f"{module_dir.name}-web"
        if not web_dir.exists():
            continue

        for java_file in sorted(web_dir.rglob("**/controller/**/*Controller.java")):
            total += 1
            try:
                if process_file(java_file):
                    modified += 1
                    print(f"  ✓ {java_file.relative_to(BACKEND_DIR)}")
            except Exception as e:
                print(f"  ✗ {java_file.relative_to(BACKEND_DIR)}: {e}")

    print(f"\n总处理: {total}, 去重修改: {modified}")


if __name__ == "__main__":
    main()