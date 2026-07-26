"""P1-10 + P1-3 批量修复脚本

P1-10: 将 ydsz-backend 下所有 *.java 文件中 @since X.Y.Z (X.Y.Z != 1.0.0) 改为 @since 1.0.0
P1-3:  从 8 个业务 web 模块的 pom.xml 删除重复的 spring-boot-starter-web 依赖块

遵循 .trae/rules/prefer-python-over-powershell.md：UTF-8 编码，无 BOM
"""
import pathlib
import re

BACKEND = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend")

# ===== P1-10: @since 版本号统一为 1.0.0 =====
SINCE_PATTERN = re.compile(r"@since\s+(\d+\.\d+\.\d+)")


def fix_since() -> tuple[int, int]:
    """返回 (修改文件数, 替换次数)"""
    file_count = 0
    replace_count = 0
    for java_file in BACKEND.rglob("*.java"):
        try:
            content = java_file.read_text(encoding="utf-8")
        except Exception as e:
            print(f"  [skip] {java_file}: {e}")
            continue

        def repl(m: re.Match) -> str:
            nonlocal replace_count
            version = m.group(1)
            if version == "1.0.0":
                return m.group(0)
            replace_count += 1
            return f"@since 1.0.0"

        new_content = SINCE_PATTERN.sub(repl, content)
        if new_content != content:
            java_file.write_text(new_content, encoding="utf-8")
            file_count += 1
    return file_count, replace_count


# ===== P1-3: 删除 8 个业务 web 模块重复的 spring-boot-starter-web =====
WEB_MODULES = [
    "ydsz-agent/ydsz-agent-web",
    "ydsz-cronjob/ydsz-cronjob-web",
    "ydsz-literule/ydsz-literule-web",
    "ydsz-message/ydsz-message-web",
    "ydsz-nextwiki/ydsz-nextwiki-web",
    "ydsz-system/ydsz-system-web",
    "ydsz-userinfo/ydsz-userinfo-web",
    "ydsz-workflow/ydsz-workflow-web",
]

# 匹配整个 <dependency> 块（含前后空白），仅当 artifactId 为 spring-boot-starter-web
DEP_PATTERN = re.compile(
    r"\n\s*<dependency>\s*\n"
    r"\s*<groupId>org\.springframework\.boot</groupId>\s*\n"
    r"\s*<artifactId>spring-boot-starter-web</artifactId>\s*\n"
    r"\s*</dependency>\s*\n",
    re.MULTILINE,
)


def fix_web_pom() -> tuple[int, int]:
    """返回 (修改文件数, 删除块数)"""
    file_count = 0
    delete_count = 0
    for rel in WEB_MODULES:
        pom = BACKEND / rel / "pom.xml"
        if not pom.exists():
            print(f"  [skip] {pom} not found")
            continue
        content = pom.read_text(encoding="utf-8")
        matches = DEP_PATTERN.findall(content)
        if not matches:
            print(f"  [noop] {pom}: no spring-boot-starter-web dependency block found")
            continue
        new_content = DEP_PATTERN.sub("\n", content)
        # 清理可能的多余空行（连续 2 个以上空行合并为 1 个）
        new_content = re.sub(r"\n{3,}", "\n\n", new_content)
        pom.write_text(new_content, encoding="utf-8")
        file_count += 1
        delete_count += len(matches)
        print(f"  [ok]   {pom}: removed {len(matches)} block(s)")
    return file_count, delete_count


if __name__ == "__main__":
    print("=== P1-10: fix @since version violations ===")
    fc, rc = fix_since()
    print(f"Modified {fc} files, replaced {rc} @since tags\n")

    print("=== P1-3: remove duplicate spring-boot-starter-web ===")
    fc, dc = fix_web_pom()
    print(f"\nModified {fc} pom.xml files, removed {dc} dependency block(s)")
