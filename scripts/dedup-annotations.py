"""
移除重复的 @SentinelRateLimit 和 @Idempotent 注解。
同一条注解在一个方法上只保留第一次出现。
"""
import pathlib
import re

BACKEND_DIR = pathlib.Path("d:/Code/ydsz/ydsz-pmis/ydsz-backend")

MODULE_KEY_MAP = {
    "ydsz-userinfo", "ydsz-workflow", "ydsz-nextwiki", "ydsz-message",
    "ydsz-cronjob", "ydsz-agent", "ydsz-literule", "ydsz-system", "ydsz-project",
}


def remove_duplicate_annotations(content: str) -> str:
    """移除方法级别重复的 @SentinelRateLimit 和 @Idempotent 注解"""
    lines = content.split("\n")
    result = []
    seen_sentinel = set()
    seen_idempotent = set()
    in_method = False

    for i, line in enumerate(lines):
        stripped = line.strip()

        # 检测方法开始
        if stripped.startswith("@SentinelRateLimit") or stripped.startswith("@Idempotent"):
            in_method = True

        # 检测方法结束 / 新方法开始标记
        # 当遇到非注解行（且非空行、非注释）时，可能是方法体或类级别
        if in_method and not stripped.startswith("@") and stripped != "" and not stripped.startswith("*") and not stripped.startswith("//") and not stripped.startswith("/*"):
            # 检查是否是 public/private/protected 方法签名
            if re.match(r'(public|private|protected)\s+', stripped):
                # 方法签名，重置计数器
                seen_sentinel.clear()
                seen_idempotent.clear()
                in_method = False
            elif not stripped.startswith("import") and not stripped.startswith("package"):
                # 可能是方法体内容，重置
                seen_sentinel.clear()
                seen_idempotent.clear()
                in_method = False

        # 跳过重复的注解
        if stripped.startswith("@SentinelRateLimit"):
            key = stripped
            if key in seen_sentinel:
                continue  # 跳过重复
            seen_sentinel.add(key)
        elif stripped.startswith("@Idempotent"):
            key = stripped
            if key in seen_idempotent:
                continue  # 跳过重复
            seen_idempotent.add(key)

        result.append(line)

    return "\n".join(result)


def process_file(file_path: pathlib.Path) -> bool:
    content = file_path.read_text(encoding="utf-8")
    new_content = remove_duplicate_annotations(content)
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