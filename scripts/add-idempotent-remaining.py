"""为 ydsz-system 和 ydsz-userinfo 缺失的 Controller 添加 @Idempotent 幂等注解"""
import pathlib
import re

PROJECT_ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis")
BACKEND = PROJECT_ROOT / "ydsz-backend"

TARGETS = [
    BACKEND / "ydsz-system/ydsz-system-web/src/main/java/com/njydsz/system/web/controller/AppInfoController.java",
    BACKEND / "ydsz-system/ydsz-system-web/src/main/java/com/njydsz/system/web/controller/ConfigController.java",
    BACKEND / "ydsz-system/ydsz-system-web/src/main/java/com/njydsz/system/web/controller/DictController.java",
    BACKEND / "ydsz-system/ydsz-system-web/src/main/java/com/njydsz/system/web/controller/DictItemController.java",
    BACKEND / "ydsz-system/ydsz-system-web/src/main/java/com/njydsz/system/web/controller/InternalApiController.java",
    BACKEND / "ydsz-system/ydsz-system-web/src/main/java/com/njydsz/system/web/controller/VariableController.java",
    BACKEND / "ydsz-userinfo/ydsz-userinfo-web/src/main/java/com/njydsz/userinfo/web/controller/AuthController.java",
    BACKEND / "ydsz-userinfo/ydsz-userinfo-web/src/main/java/com/njydsz/userinfo/web/controller/CaptchaController.java",
]

IDEMPOTENT_IMPORT = "import com.njydsz.common.lock.annotation.Idempotent;\n"

fixed = 0
for filepath in TARGETS:
    if not filepath.exists():
        print(f"  [SKIP] 不存在: {filepath.name}")
        continue

    content = filepath.read_text(encoding="utf-8")
    if "@Idempotent" in content:
        print(f"  [SKIP] 已有: {filepath.name}")
        continue

    rel = str(filepath.relative_to(PROJECT_ROOT))
    if "ydsz-system" in rel:
        module = "system"
    else:
        module = "userinfo"

    class_name = filepath.stem.replace("Controller", "").lower()
    modified = [False]  # 使用 list 避免 nonlocal 问题

    # 匹配: @RateLimit(...)\n    @PostMapping\n    public ... methodName(
    def replace_with_ratelimit(match):
        indent = match.group(2)
        method_name = match.group(3)
        key = f"'{module}:{class_name}:{method_name}'"
        idempotent_line = f'{indent}@Idempotent(key = {key}, ttlSeconds = 5, message = "请勿重复提交")\n'
        modified[0] = True
        return idempotent_line + match.group(1)

    pattern = re.compile(
        r'((\s+)@RateLimit\([^)]+\)\n'
        r'\2@(?:Post|Put|Delete)Mapping[^\n]*\n'
        r'\2public\s+\S+\s+(\w+)\s*\([^)]*\)\s*\{)',
        re.MULTILINE
    )

    new_content = pattern.sub(replace_with_ratelimit, content)

    # 如果没有 @RateLimit，匹配纯 @PostMapping
    if not modified[0]:
        def replace_no_ratelimit(match):
            indent = match.group(2)
            method_name = match.group(3)
            key = f"'{module}:{class_name}:{method_name}'"
            idempotent_line = f'{indent}@Idempotent(key = {key}, ttlSeconds = 5, message = "请勿重复提交")\n'
            modified[0] = True
            return idempotent_line + match.group(1)

        pattern2 = re.compile(
            r'((\s+)@(?:Post|Put|Delete)Mapping[^\n]*\n'
            r'\2public\s+\S+\s+(\w+)\s*\([^)]*\)\s*\{)',
            re.MULTILINE
        )

        new_content = pattern2.sub(replace_no_ratelimit, new_content)

    if not modified[0]:
        print(f"  [SKIP] 无匹配: {filepath.name}")
        continue

    # 添加 import
    if "import com.njydsz.common.lock.annotation.Idempotent;" not in new_content:
        import_pattern = re.compile(r'(^import\s+.+?;\s*\n)', re.MULTILINE)
        first_import = import_pattern.search(new_content)
        if first_import:
            insert_pos = first_import.end()
            new_content = new_content[:insert_pos] + IDEMPOTENT_IMPORT + new_content[insert_pos:]

    filepath.write_text(new_content, encoding="utf-8")
    fixed += 1
    print(f"  [FIXED] {filepath.name}")

print(f"\n修复完成: {fixed} 个文件")