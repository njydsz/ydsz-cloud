"""
P0-1 清理业务模块冗余审计字段与逻辑删除过滤代码

整改目标：
1. 删除业务代码中冗余的 `wrapper.eq(Entity::getDeleted, 0)` 显式过滤
   —— LogicalDeleteInterceptor 已自动追加 `deleted = 0` 条件
2. 删除业务代码中手填的 `entity.setCreatedAt(LocalDateTime.now())` 和 `entity.setUpdatedAt(LocalDateTime.now())`
   —— MyMetaObjectHandler 已通过 @TableField(fill=FieldFill.INSERT/INSERT_UPDATE) 自动填充

不处理（需个案判断）：
- setCreatedBy(userId) / setUpdatedBy(userId) —— userId 可能不是当前登录用户
- entity.setDeleted(1) 手填逻辑删除 —— 应改为 mapper.deleteById() 让拦截器转换，风险较高留到后续

扫描范围：ydsz-backend 下的业务模块 ServiceImpl/Service/DomainService/ApplicationService
排除范围：ydsz-common（基础设施代码）
"""
import pathlib
import re
import sys

ROOT = pathlib.Path("d:/Code/ydsz/ydsz-pmis/ydsz-backend")

# 排除 ydsz-common 子模块（基础设施自身代码不清理）
EXCLUDE_DIR_PARTS = ("ydsz-common",)

# 仅处理这些子目录下的 Java 文件（业务层 + 领域层）
INCLUDE_SUFFIXES = (
    "ServiceImpl.java",
    "Service.java",
    "DomainService.java",
    "ApplicationService.java",
    "RepositoryImpl.java",
)

# Pattern 1: wrapper.eq(Entity::getDeleted, 0)  —— 冗余逻辑删除过滤
# 匹配：xxx.eq(AnyClass::getDeleted, 0)  或  xxx.eq(AnyClass::getDeleted,0)
# 注意：仅匹配值为 0 的（normal value），不匹配值为 1 的（业务可能用于查询已删除）
PATTERN_DELETED_EQ = re.compile(
    r"^\s*\w+\.eq\(\s*\w+::getDeleted\s*,\s*0\s*\)\s*;?\s*$"
)

# Pattern 2: entity.setCreatedAt(LocalDateTime.now());  —— 手填创建时间
PATTERN_SET_CREATED_AT = re.compile(
    r"^\s*\w+\.setCreatedAt\(\s*LocalDateTime\.now\(\)\s*\)\s*;?\s*$"
)

# Pattern 3: entity.setUpdatedAt(LocalDateTime.now());  —— 手填更新时间
PATTERN_SET_UPDATED_AT = re.compile(
    r"^\s*\w+\.setUpdatedAt\(\s*LocalDateTime\.now\(\)\s*\)\s*;?\s*$"
)

# 多余空行收敛（连续 2+ 空行收敛为 1 空行）
PATTERN_MULTI_BLANK = re.compile(r"\n{3,}")


def should_process(path: pathlib.Path) -> bool:
    """判断文件是否需要处理"""
    # 必须是 Java 文件
    if not path.name.endswith(".java"):
        return False
    # 排除 ydsz-common
    parts = path.parts
    if any(part in EXCLUDE_DIR_PARTS for part in parts):
        return False
    # 仅处理 src/main/java 下的源文件
    if "src" not in parts or "main" not in parts or "java" not in parts:
        return False
    # 仅处理指定后缀
    return any(path.name.endswith(suffix) for suffix in INCLUDE_SUFFIXES)


def process_file(path: pathlib.Path) -> tuple[int, int, int]:
    """
    处理单个文件
    返回 (deleted_eq_count, set_created_at_count, set_updated_at_count)
    """
    original = path.read_text(encoding="utf-8")
    lines = original.split("\n")

    deleted_eq_count = 0
    set_created_at_count = 0
    set_updated_at_count = 0

    new_lines = []
    for line in lines:
        if PATTERN_DELETED_EQ.match(line):
            deleted_eq_count += 1
            continue  # 跳过该行（删除）
        if PATTERN_SET_CREATED_AT.match(line):
            set_created_at_count += 1
            continue
        if PATTERN_SET_UPDATED_AT.match(line):
            set_updated_at_count += 1
            continue
        new_lines.append(line)

    if deleted_eq_count == 0 and set_created_at_count == 0 and set_updated_at_count == 0:
        return (0, 0, 0)

    # 收敛多余空行
    new_content = "\n".join(new_lines)
    new_content = PATTERN_MULTI_BLANK.sub("\n\n", new_content)

    # 确保文件末尾保留单个换行符
    new_content = new_content.rstrip() + "\n"

    path.write_text(new_content, encoding="utf-8")
    return (deleted_eq_count, set_created_at_count, set_updated_at_count)


def main():
    total_files_changed = 0
    total_deleted_eq = 0
    total_set_created_at = 0
    total_set_updated_at = 0

    print("=" * 80)
    print("P0-1 清理冗余审计字段与逻辑删除过滤")
    print("=" * 80)

    # 扫描所有 Java 文件
    for path in ROOT.rglob("*.java"):
        if not should_process(path):
            continue
        try:
            d, c, u = process_file(path)
        except Exception as e:
            print(f"[ERROR] {path}: {e}", file=sys.stderr)
            continue

        if d > 0 or c > 0 or u > 0:
            total_files_changed += 1
            total_deleted_eq += d
            total_set_created_at += c
            total_set_updated_at += u
            rel = path.relative_to(ROOT)
            print(f"[CHANGED] {rel}: deleted_eq={d}, set_created_at={c}, set_updated_at={u}")

    print("=" * 80)
    print(f"Total files changed:           {total_files_changed}")
    print(f"Total .eq(Entity::getDeleted, 0) removed:  {total_deleted_eq}")
    print(f"Total setCreatedAt(now) removed:           {total_set_created_at}")
    print(f"Total setUpdatedAt(now) removed:           {total_set_updated_at}")
    print("=" * 80)


if __name__ == "__main__":
    main()
