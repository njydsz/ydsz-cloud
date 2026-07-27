"""P0-1: 批量移除业务实体中重复声明的 @TableLogic deleted 字段。

背景：
    MpBaseEntity 已声明 @TableField("deleted") private Integer deleted;
    并明确注释"由 LogicalDeleteInterceptor 处理，不使用 @TableLogic，避免双重处理冲突"。
    但 23 个业务实体子类仍重复声明 @TableLogic + deleted，存在双重处理风险。

处理规则：
    1. 移除 import com.baomidou.mybatisplus.annotation.TableLogic;
    2. 移除 @TableLogic 注解行
    3. 对于 private Integer deleted;（与父类冲突）→ 整行移除
    4. 对于 private Integer historyDeleted;（JobHistoryDO 业务字段）→ 保留字段，仅移除注解
    5. 移除紧邻 @TableLogic 前的"逻辑删除"注释行
    6. 清理多余空行

遵循项目硬约束：
    - 使用 Python 而非 PowerShell（避免编码损坏）
    - UTF-8 编码读写
    - 不引入 pmis 标识
"""
import pathlib
import re

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend")

# 23 个需要处理的业务实体文件（绝对路径）
FILES = [
    # ydsz-userinfo-domain (13 个)
    ROOT / "ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/UserRoleDO.java",
    ROOT / "ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/UserPostDO.java",
    ROOT / "ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/UserFieldDO.java",
    ROOT / "ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/UserDeptDO.java",
    ROOT / "ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/UserAccountDO.java",
    ROOT / "ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/RolePermissionDO.java",
    ROOT / "ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/RoleDO.java",
    ROOT / "ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/PostDO.java",
    ROOT / "ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/MenuDO.java",
    ROOT / "ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/LanguageDO.java",
    ROOT / "ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/DepartmentDO.java",
    ROOT / "ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/CompanyDO.java",
    ROOT / "ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/entity/CompanyDeptDO.java",
    # ydsz-system-domain (9 个)
    ROOT / "ydsz-system/ydsz-system-domain/src/main/java/com/njydsz/system/domain/entity/VariableDO.java",
    ROOT / "ydsz-system/ydsz-system-domain/src/main/java/com/njydsz/system/domain/entity/TenantPlanMenuDO.java",
    ROOT / "ydsz-system/ydsz-system-domain/src/main/java/com/njydsz/system/domain/entity/TenantPlanDO.java",
    ROOT / "ydsz-system/ydsz-system-domain/src/main/java/com/njydsz/system/domain/entity/TenantDO.java",
    ROOT / "ydsz-system/ydsz-system-domain/src/main/java/com/njydsz/system/domain/entity/DictVersionDO.java",
    ROOT / "ydsz-system/ydsz-system-domain/src/main/java/com/njydsz/system/domain/entity/DictTypeDO.java",
    ROOT / "ydsz-system/ydsz-system-domain/src/main/java/com/njydsz/system/domain/entity/DictItemDO.java",
    ROOT / "ydsz-system/ydsz-system-domain/src/main/java/com/njydsz/system/domain/entity/ConfigDO.java",
    ROOT / "ydsz-system/ydsz-system-domain/src/main/java/com/njydsz/system/domain/entity/AppInfoDO.java",
    # ydsz-cronjob-domain (1 个，特例：historyDeleted 业务字段)
    ROOT / "ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/entity/job/JobHistoryDO.java",  # noqa
]


def process_file(path: pathlib.Path) -> dict:
    """处理单个文件，返回处理结果统计。"""
    result = {"file": path.name, "import_removed": 0, "annotation_removed": 0,
              "field_removed": 0, "comment_removed": 0, "history_deleted_kept": False}

    content = path.read_text(encoding="utf-8")
    original = content

    # 1. 移除 import TableLogic 行
    new_content, n = re.subn(
        r"^import com\.baomidou\.mybatisplus\.annotation\.TableLogic;\n",
        "",
        content,
        flags=re.MULTILINE,
    )
    result["import_removed"] = n
    content = new_content

    # 2. 移除前置"逻辑删除"注释 + @TableLogic 注解 + deleted 字段（整块移除）
    # 情况 A: 有前置 /** ...逻辑删除... */ 注释 + @TableLogic + private Integer deleted;
    pattern_a = (
        r"    /\*\*[^*]*逻辑删除[^*]*\*/\n"
        r"    @TableLogic\n"
        r"    private Integer deleted;\n"
        r"\n?"  # 后随空行
    )
    new_content, n_a = re.subn(pattern_a, "", content)
    result["comment_removed"] += n_a
    result["annotation_removed"] += n_a
    result["field_removed"] += n_a
    content = new_content

    # 情况 B: 无前置注释 + @TableLogic + private Integer deleted;
    pattern_b = (
        r"    @TableLogic\n"
        r"    private Integer deleted;\n"
        r"\n?"
    )
    new_content, n_b = re.subn(pattern_b, "", content)
    result["annotation_removed"] += n_b
    result["field_removed"] += n_b
    content = new_content

    # 情况 C（特例 JobHistoryDO）: 有前置注释 + @TableLogic + private Integer historyDeleted;
    # 保留 historyDeleted 字段（业务字段），仅移除注释和注解
    pattern_c = (
        r"    /\*\*[^*]*逻辑删除[^*]*\*/\n"
        r"    @TableLogic\n"
        r"    private Integer historyDeleted;"
    )
    replacement_c = "    /** 历史版本删除标记（业务字段，非逻辑删除标识；逻辑删除由父类 deleted 字段处理） */\n    private Integer historyDeleted;"
    new_content, n_c = re.subn(pattern_c, replacement_c, content)
    if n_c > 0:
        result["comment_removed"] += n_c
        result["annotation_removed"] += n_c
        result["history_deleted_kept"] = True
    content = new_content

    # 情况 D（特例兜底）: 无前置注释 + @TableLogic + private Integer historyDeleted;
    pattern_d = (
        r"    @TableLogic\n"
        r"    private Integer historyDeleted;"
    )
    replacement_d = "    private Integer historyDeleted;"
    new_content, n_d = re.subn(pattern_d, replacement_d, content)
    if n_d > 0:
        result["annotation_removed"] += n_d
        result["history_deleted_kept"] = True
    content = new_content

    # 3. 清理多余空行（连续 3 个以上换行压缩为 2 个）
    content = re.sub(r"\n{3,}", "\n\n", content)

    if content != original:
        path.write_text(content, encoding="utf-8")
        result["changed"] = True
    else:
        result["changed"] = False

    return result


def main():
    print("=" * 80)
    print("P0-1: 批量移除业务实体重复声明的 @TableLogic deleted 字段")
    print("=" * 80)

    total_changed = 0
    total_import = 0
    total_annotation = 0
    total_field = 0
    total_comment = 0

    for path in FILES:
        if not path.exists():
            print(f"  [缺失] {path.name}: 文件不存在")
            continue

        result = process_file(path)

        if result["changed"]:
            total_changed += 1
            total_import += result["import_removed"]
            total_annotation += result["annotation_removed"]
            total_field += result["field_removed"]
            total_comment += result["comment_removed"]

            status = []
            if result["import_removed"]:
                status.append(f"import-{result['import_removed']}")
            if result["annotation_removed"]:
                status.append(f"注解-{result['annotation_removed']}")
            if result["field_removed"]:
                status.append(f"字段-{result['field_removed']}")
            if result["comment_removed"]:
                status.append(f"注释-{result['comment_removed']}")
            if result["history_deleted_kept"]:
                status.append("historyDeleted保留")

            print(f"  [已改] {result['file']}: {', '.join(status)}")
        else:
            print(f"  [未改] {result['file']}: 无匹配")

    print("-" * 80)
    print(f"汇总: 修改文件 {total_changed}/{len(FILES)} 个")
    print(f"      移除 import {total_import} 处")
    print(f"      移除 @TableLogic 注解 {total_annotation} 处")
    print(f"      移除 deleted 字段 {total_field} 处")
    print(f"      移除 逻辑删除注释 {total_comment} 处")
    print("=" * 80)


if __name__ == "__main__":
    main()
