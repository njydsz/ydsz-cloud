"""
P1 阶段：将 sales / finance 模块的 Java 文件物理迁移到 project 模块，并扁平化包路径。

规则：
  com.njydsz.pmis.sales    → com.njydsz.pmis.project
  com.njydsz.pmis.finance  → com.njydsz.pmis.project

类名冲突处理（保留 project 已有版本，删除 sales/finance 版本）：
  - sales/server/assembler/NameAssembler.java        → 删除（project 已有）
  - sales/domain/enums/RiskLevel.java                → 删除（project 已有）
  - finance/server/engine/AlertCodeGen.java          → 删除（project 已有）
  - sales/server/dto/InitiationCreateDTO.java        → 删除（孤儿类，注释"暂存待迁移"，project-api 已有）

不迁移的文件（启动类，合并到 ProjectApplication）：
  - sales/web/SalesApplication.java
  - finance/web/FinanceApplication.java

Mapper XML 迁移：
  - sales-infra/resources/mapper/contract/*    → project-infra/resources/mapper/sales/contract/*
  - sales-infra/resources/mapper/opportunity/* → project-infra/resources/mapper/sales/opportunity/*
  - finance-infra/resources/mapper/finance/*   → project-infra/resources/mapper/finance/*
"""

import pathlib
import shutil
import sys

BACKEND_ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend")

# 源模块根目录
SALES_ROOT = BACKEND_ROOT / "ydsz-pmis-sales"
FINANCE_ROOT = BACKEND_ROOT / "ydsz-pmis-finance"
PROJECT_ROOT = BACKEND_ROOT / "ydsz-pmis-project"

# 子模块映射：(源子模块名, 目标子模块名)
SUBMODULE_MAP = {
    "ydsz-pmis-sales-api": "ydsz-pmis-project-api",
    "ydsz-pmis-sales-domain": "ydsz-pmis-project-domain",
    "ydsz-pmis-sales-infra": "ydsz-pmis-project-infra",
    "ydsz-pmis-sales-server": "ydsz-pmis-project-server",
    "ydsz-pmis-sales-web": "ydsz-pmis-project-web",
    "ydsz-pmis-finance-api": "ydsz-pmis-project-api",
    "ydsz-pmis-finance-domain": "ydsz-pmis-project-domain",
    "ydsz-pmis-finance-infra": "ydsz-pmis-project-infra",
    "ydsz-pmis-finance-server": "ydsz-pmis-project-server",
    "ydsz-pmis-finance-web": "ydsz-pmis-project-web",
}

# 需要删除的文件（类名冲突或孤儿类）
FILES_TO_DELETE = [
    # sales NameAssembler 与 project 冲突
    SALES_ROOT / "ydsz-pmis-sales-server" / "src" / "main" / "java" / "com" / "njydsz" / "pmis" / "sales" / "server" / "assembler" / "NameAssembler.java",
    # sales RiskLevel 与 project 冲突
    SALES_ROOT / "ydsz-pmis-sales-domain" / "src" / "main" / "java" / "com" / "njydsz" / "pmis" / "sales" / "domain" / "enums" / "RiskLevel.java",
    # finance AlertCodeGen 与 project 冲突
    FINANCE_ROOT / "ydsz-pmis-finance-server" / "src" / "main" / "java" / "com" / "njydsz" / "pmis" / "finance" / "server" / "engine" / "AlertCodeGen.java",
    # sales 孤儿 InitiationCreateDTO（错误包名 com.njydsz.pmis.server.dto，project-api 已有）
    SALES_ROOT / "ydsz-pmis-sales-server" / "src" / "main" / "java" / "com" / "njydsz" / "pmis" / "server" / "dto" / "InitiationCreateDTO.java",
    # sales 孤儿 InitiationCreateDTO 的错误目录（com/njydsz/pmis/server/dto/）
]

# 不迁移的文件（启动类）
FILES_TO_SKIP = [
    SALES_ROOT / "ydsz-pmis-sales-web" / "src" / "main" / "java" / "com" / "njydsz" / "pmis" / "sales" / "web" / "SalesApplication.java",
    FINANCE_ROOT / "ydsz-pmis-finance-web" / "src" / "main" / "java" / "com" / "njydsz" / "pmis" / "finance" / "web" / "FinanceApplication.java",
]

# 包路径替换规则（按长度降序，避免子串误匹配）
PACKAGE_REPLACEMENTS = [
    ("com.njydsz.pmis.sales", "com.njydsz.pmis.project"),
    ("com.njydsz.pmis.finance", "com.njydsz.pmis.project"),
]

# 目录路径替换规则（用于计算目标文件路径）
DIR_REPLACEMENTS = [
    (pathlib.PurePath("com/njydsz/pmis/sales"), pathlib.PurePath("com/njydsz/pmis/project")),
    (pathlib.PurePath("com/njydsz/pmis/finance"), pathlib.PurePath("com/njydsz/pmis/project")),
]


def should_delete(file_path: pathlib.Path) -> bool:
    """检查文件是否在删除列表中"""
    for del_path in FILES_TO_DELETE:
        try:
            if file_path.resolve() == del_path.resolve():
                return True
        except Exception:
            pass
    return False


def should_skip(file_path: pathlib.Path) -> bool:
    """检查文件是否在跳过列表中"""
    for skip_path in FILES_TO_SKIP:
        try:
            if file_path.resolve() == skip_path.resolve():
                return True
        except Exception:
            pass
    return False


def compute_target_path(src_file: pathlib.Path, src_module_root: pathlib.Path, src_submodule: str) -> pathlib.Path:
    """
    计算目标文件路径。

    源路径示例：
      d:\...\ydsz-pmis-sales\ydsz-pmis-sales-server\src\main\java\com\njydsz\pmis\sales\server\service\contract\ContractService.java
    目标路径：
      d:\...\ydsz-pmis-project\ydsz-pmis-project-server\src\main\java\com\njydsz\pmis\project\server\service\contract\ContractService.java

    逻辑：
      1. 取相对路径（相对于源子模块根）
      2. 替换子模块名
      3. 替换目录路径中的 sales/finance → project
    """
    # 相对于源子模块根的路径
    rel_path = src_file.relative_to(src_module_root)
    # 替换子模块目录名
    rel_parts = list(rel_path.parts)
    # rel_parts[0] 是子模块目录名，替换为目标子模块名
    target_submodule = SUBMODULE_MAP.get(src_submodule)
    if target_submodule is None:
        raise ValueError(f"未知子模块: {src_submodule}")
    rel_parts[0] = target_submodule

    # 在路径部分中替换 sales/finance → project
    new_parts = []
    for part in rel_parts:
        if part == "sales" or part == "finance":
            # 只替换 com/njydsz/pmis/ 下的 sales/finance
            # 检查前一个 part 是否为 pmis
            if len(new_parts) >= 1 and new_parts[-1] == "pmis":
                new_parts.append("project")
            else:
                new_parts.append(part)
        else:
            new_parts.append(part)

    target_path = PROJECT_ROOT.joinpath(*new_parts)
    return target_path


def migrate_java_file(src_file: pathlib.Path, src_module_root: pathlib.Path, src_submodule: str) -> bool:
    """
    迁移单个 Java 文件：
      1. 读取内容
      2. 替换包路径
      3. 计算目标路径
      4. 写入目标
      5. 删除源文件
    返回 True 表示已处理，False 表示跳过。
    """
    if should_delete(src_file):
        print(f"  [DELETE] {src_file.relative_to(BACKEND_ROOT)}")
        src_file.unlink()
        return True

    if should_skip(src_file):
        print(f"  [SKIP]   {src_file.relative_to(BACKEND_ROOT)}")
        return False

    content = src_file.read_text(encoding="utf-8")

    # 替换包路径
    new_content = content
    for old, new in PACKAGE_REPLACEMENTS:
        new_content = new_content.replace(old, new)

    # 计算目标路径
    target_path = compute_target_path(src_file, src_module_root, src_submodule)

    # 如果内容没变且目标已存在，跳过
    if target_path.exists() and target_path.read_text(encoding="utf-8") == new_content:
        print(f"  [EXISTS] {target_path.relative_to(BACKEND_ROOT)}")
        src_file.unlink()
        return True

    # 创建目标目录
    target_path.parent.mkdir(parents=True, exist_ok=True)

    # 写入目标文件
    target_path.write_text(new_content, encoding="utf-8")
    print(f"  [MIGRATE] {src_file.relative_to(BACKEND_ROOT)} → {target_path.relative_to(BACKEND_ROOT)}")

    # 删除源文件
    src_file.unlink()
    return True


def migrate_mapper_xml(src_module_root: pathlib.Path, src_submodule: str):
    """
    迁移 Mapper XML 文件。

    sales-infra/resources/mapper/contract/*     → project-infra/resources/mapper/sales/contract/*
    sales-infra/resources/mapper/opportunity/*  → project-infra/resources/mapper/sales/opportunity/*
    finance-infra/resources/mapper/finance/*    → project-infra/resources/mapper/finance/*
    """
    resources_mapper = src_module_root / "src" / "main" / "resources" / "mapper"
    if not resources_mapper.exists():
        return

    target_submodule = SUBMODULE_MAP[src_submodule]
    target_mapper_root = PROJECT_ROOT / target_submodule / "src" / "main" / "resources" / "mapper"

    # 确定来源模块名
    if "sales" in src_submodule:
        source_module_name = "sales"
    elif "finance" in src_submodule:
        source_module_name = "finance"
    else:
        source_module_name = None

    for xml_file in resources_mapper.rglob("*.xml"):
        # 计算相对路径
        rel_path = xml_file.relative_to(resources_mapper)
        # 如果是 sales 模块，在 mapper 下加 sales 子目录
        if source_module_name == "sales":
            # sales 的 mapper 已经有 contract/ opportunity/ 子目录，统一放到 sales/ 下
            target_path = target_mapper_root / "sales" / rel_path
        elif source_module_name == "finance":
            # finance 的 mapper 已经有 finance/ 子目录
            target_path = target_mapper_root / "finance" / rel_path
        else:
            target_path = target_mapper_root / rel_path

        target_path.parent.mkdir(parents=True, exist_ok=True)
        target_path.write_text(xml_file.read_text(encoding="utf-8"), encoding="utf-8")
        print(f"  [XML]     {xml_file.relative_to(BACKEND_ROOT)} → {target_path.relative_to(BACKEND_ROOT)}")
        xml_file.unlink()


def migrate_resources(src_module_root: pathlib.Path, src_submodule: str):
    """迁移非 Java、非 Mapper XML 的资源文件（如 META-INF）"""
    resources_dir = src_module_root / "src" / "main" / "resources"
    if not resources_dir.exists():
        return

    target_submodule = SUBMODULE_MAP[src_submodule]
    target_resources = PROJECT_ROOT / target_submodule / "src" / "main" / "resources"

    for res_file in resources_dir.rglob("*"):
        if res_file.is_file() and res_file.suffix != ".xml":
            rel_path = res_file.relative_to(resources_dir)
            target_path = target_resources / rel_path
            target_path.parent.mkdir(parents=True, exist_ok=True)
            target_path.write_text(res_file.read_text(encoding="utf-8"), encoding="utf-8")
            print(f"  [RES]     {res_file.relative_to(BACKEND_ROOT)} → {target_path.relative_to(BACKEND_ROOT)}")
            res_file.unlink()
        elif res_file.is_file() and res_file.suffix == ".xml" and "mapper" not in str(res_file):
            # 非 mapper 的 XML 文件
            rel_path = res_file.relative_to(resources_dir)
            target_path = target_resources / rel_path
            target_path.parent.mkdir(parents=True, exist_ok=True)
            target_path.write_text(res_file.read_text(encoding="utf-8"), encoding="utf-8")
            print(f"  [RES-XML] {res_file.relative_to(BACKEND_ROOT)} → {target_path.relative_to(BACKEND_ROOT)}")
            res_file.unlink()


def migrate_test_files(src_module_root: pathlib.Path, src_submodule: str):
    """迁移测试文件"""
    test_dir = src_module_root / "src" / "test"
    if not test_dir.exists():
        return

    target_submodule = SUBMODULE_MAP[src_submodule]
    target_test = PROJECT_ROOT / target_submodule / "src" / "test"

    for test_file in test_dir.rglob("*.java"):
        content = test_file.read_text(encoding="utf-8")
        new_content = content
        for old, new in PACKAGE_REPLACEMENTS:
            new_content = new_content.replace(old, new)

        rel_path = test_file.relative_to(test_dir)

        # 替换路径中的 sales/finance → project
        new_parts = []
        for part in rel_path.parts:
            if part == "sales" or part == "finance":
                if len(new_parts) >= 1 and new_parts[-1] == "pmis":
                    new_parts.append("project")
                else:
                    new_parts.append(part)
            else:
                new_parts.append(part)

        target_path = target_test.joinpath(*new_parts)
        target_path.parent.mkdir(parents=True, exist_ok=True)
        target_path.write_text(new_content, encoding="utf-8")
        print(f"  [TEST]    {test_file.relative_to(BACKEND_ROOT)} → {target_path.relative_to(BACKEND_ROOT)}")
        test_file.unlink()


def process_module(module_root: pathlib.Path, module_name: str):
    """处理一个模块的所有子模块"""
    print(f"\n{'='*60}")
    print(f"处理模块: {module_name}")
    print(f"{'='*60}")

    # 遍历子模块
    for submodule_dir in sorted(module_root.iterdir()):
        if not submodule_dir.is_dir() or not submodule_dir.name.startswith("ydsz-pmis-"):
            continue

        src_submodule = submodule_dir.name
        if src_submodule not in SUBMODULE_MAP:
            continue

        print(f"\n--- 子模块: {src_submodule} ---")

        # 迁移 Java 文件
        java_root = submodule_dir / "src" / "main" / "java"
        if java_root.exists():
            for java_file in sorted(java_root.rglob("*.java")):
                migrate_java_file(java_file, submodule_dir, src_submodule)

        # 迁移测试文件
        migrate_test_files(submodule_dir, src_submodule)

        # 迁移 Mapper XML
        migrate_mapper_xml(submodule_dir, src_submodule)

        # 迁移其他资源文件
        migrate_resources(submodule_dir, src_submodule)


def cleanup_empty_dirs():
    """清理空目录"""
    print(f"\n{'='*60}")
    print("清理空目录")
    print(f"{'='*60}")

    for module_root in [SALES_ROOT, FINANCE_ROOT]:
        if not module_root.exists():
            continue
        # 从最深层开始删除空目录
        for dirpath in sorted(module_root.rglob("*"), reverse=True):
            if dirpath.is_dir():
                try:
                    # 检查目录是否为空
                    next(dirpath.iterdir(), None)
                except StopIteration:
                    pass
                else:
                    continue
                # 目录为空，删除
                try:
                    dirpath.rmdir()
                    print(f"  [RMDIR]  {dirpath.relative_to(BACKEND_ROOT)}")
                except OSError:
                    pass


def main():
    print("=" * 60)
    print("P1: sales / finance → project 物理迁移 + 包路径扁平化")
    print("=" * 60)

    # 检查源目录存在
    if not SALES_ROOT.exists():
        print(f"ERROR: sales 模块不存在: {SALES_ROOT}")
        sys.exit(1)
    if not FINANCE_ROOT.exists():
        print(f"ERROR: finance 模块不存在: {FINANCE_ROOT}")
        sys.exit(1)
    if not PROJECT_ROOT.exists():
        print(f"ERROR: project 模块不存在: {PROJECT_ROOT}")
        sys.exit(1)

    # 处理 sales 模块
    process_module(SALES_ROOT, "ydsz-pmis-sales")

    # 处理 finance 模块
    process_module(FINANCE_ROOT, "ydsz-pmis-finance")

    # 清理空目录
    cleanup_empty_dirs()

    print(f"\n{'='*60}")
    print("P1 迁移完成")
    print("=" * 60)
    print("\n下一步需要手动处理：")
    print("1. 更新 project-server/pom.xml 合并依赖")
    print("2. 更新 ProjectApplication.java 启动类")
    print("3. 合并 bootstrap.yml 和环境配置")
    print("4. 合并 SQL 文件（P2）")
    print("5. Feign 契约下线（P3）")
    print("6. API 路径统一（P4）")


if __name__ == "__main__":
    main()
