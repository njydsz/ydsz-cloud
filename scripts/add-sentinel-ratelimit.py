"""
批量添加 @RateLimit 注解到所有业务模块的 Controller 方法
目标：POST/PUT/DELETE 方法添加限流保护
"""
import pathlib
import re
import sys

PROJECT_ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis")
BACKEND = PROJECT_ROOT / "ydsz-backend"

# 业务模块（排除 common / gateway）
BUSINESS_MODULES = [
    "ydsz-system",
    "ydsz-userinfo",
    "ydsz-project",
    "ydsz-workflow",
    "ydsz-literule",
    "ydsz-message",
    "ydsz-cronjob",
    "ydsz-agent",
    "ydsz-nextwiki",
]

# 需要跳过的目录
SKIP_DIRS = {"target", "test", "node_modules", ".git", "scripts"}

stats = {"scanned": 0, "modified": 0, "skipped": 0, "errors": []}


def find_controllers(module: str) -> list[pathlib.Path]:
    """查找模块下的所有 Controller 文件"""
    module_dir = BACKEND / module
    if not module_dir.exists():
        return []
    controllers = []
    for f in module_dir.rglob("**/controller/**/*Controller.java"):
        skip = False
        for part in f.parts:
            if part in SKIP_DIRS:
                skip = True
                break
        if not skip:
            controllers.append(f)
    return controllers


def extract_imports(content: str) -> list[str]:
    """提取所有 import 语句"""
    return re.findall(r'^import\s+(.+?);', content, re.MULTILINE)


def has_annotation(content: str, annotation: str) -> bool:
    """检查是否已有指定注解"""
    return annotation in content


def add_ratelimit_to_file(filepath: pathlib.Path) -> bool:
    """为单个 Controller 文件添加 @RateLimit 注解"""
    content = filepath.read_text(encoding="utf-8")
    original = content

    # 提取模块名
    parts = filepath.parts
    module_idx = None
    for i, p in enumerate(parts):
        if p in BUSINESS_MODULES:
            module_idx = i
            break
    if module_idx is None:
        return False

    module_name = parts[module_idx]
    # 去掉 ydsz- 前缀
    clean_module = module_name.replace("ydsz-", "")

    # 获取类名
    class_name = filepath.stem  # 去掉 .java

    # 检查是否已有 @RateLimit import
    has_import = "import com.njydsz.common.safe.ratelimit.annotation.RateLimit;" in content

    # 匹配 @PostMapping / @PutMapping / @DeleteMapping 方法
    # 匹配方法签名模式：@XxxMapping(...) 后跟 public ... methodName(...)
    method_pattern = re.compile(
        r'(\s+)(@(?:Post|Put|Delete)Mapping[^\n]*\n(?:\s+@\w+[^\n]*\n)*)'
        r'(\s+public\s+\S+\s+(\w+)\s*\([^)]*\)\s*\{)',
        re.MULTILINE
    )

    modified = False
    new_content = content

    # 为每个 POST/PUT/DELETE 方法添加 @RateLimit
    def replace_method(match):
        nonlocal modified
        indent = match.group(1)
        mapping_annotations = match.group(2)
        method_sig = match.group(3)
        method_name = match.group(4)

        # 检查是否已有 @RateLimit
        if "@RateLimit" in mapping_annotations:
            return match.group(0)

        # 构造资源名
        resource = f"{clean_module}.{class_name.replace('Controller', '').lower()}.{method_name}"

        # 构造 @RateLimit 注解
        ratelimit_anno = (
            f'{indent}@RateLimit(resource = "{resource}", threshold = 50)\n'
        )

        modified = True
        return ratelimit_anno + indent + mapping_annotations + method_sig

    new_content = method_pattern.sub(replace_method, new_content)

    if not modified:
        return False

    # 添加 import（如果还没有）
    if not has_import:
        # 找到第一个 import 行之后插入
        import_pattern = re.compile(r'(^import\s+.+?;\s*\n)', re.MULTILINE)
        first_import = import_pattern.search(new_content)
        if first_import:
            insert_pos = first_import.end()
            new_content = (
                new_content[:insert_pos]
                + "import com.njydsz.common.safe.ratelimit.annotation.RateLimit;\n"
                + new_content[insert_pos:]
            )

    # 写回文件
    filepath.write_text(new_content, encoding="utf-8")
    return True


def main():
    print("=" * 60)
    print("批量添加 @RateLimit 限流注解")
    print("=" * 60)

    for module in BUSINESS_MODULES:
        controllers = find_controllers(module)
        if not controllers:
            print(f"  [{module}] 无 Controller 文件")
            continue

        for ctrl in controllers:
            stats["scanned"] += 1
            try:
                if add_ratelimit_to_file(ctrl):
                    stats["modified"] += 1
                    rel = ctrl.relative_to(PROJECT_ROOT)
                    print(f"  [MODIFIED] {rel}")
            except Exception as e:
                stats["errors"].append(f"{ctrl}: {e}")
                print(f"  [ERROR] {ctrl}: {e}")

    print()
    print(f"扫描: {stats['scanned']} | 修改: {stats['modified']} | 错误: {len(stats['errors'])}")
    if stats["errors"]:
        print("错误详情:")
        for err in stats["errors"]:
            print(f"  - {err}")


if __name__ == "__main__":
    main()