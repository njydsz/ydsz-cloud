"""
P0 整改：为 ydsz-project 模块所有写操作添加 @Idempotent 注解。

策略：
1. 扫描 ydsz-project-web 下所有 Controller 类
2. 对每个 @PostMapping、@PutMapping、@DeleteMapping 方法添加 @Idempotent
3. Key 命名规则：ydsz:project:{ControllerSimpleName}:{methodName}:lock
4. 自动添加 import 语句
"""

import pathlib
import re
import sys

PROJECT_WEB = pathlib.Path(
    r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-project\ydsz-project-web\src\main\java\com\njydsz\project\web\controller"
)

IDEMPOTENT_IMPORT = "import com.njydsz.common.lock.annotation.Idempotent;"

# HTTP method annotations that indicate a write operation
WRITE_ANNOTATIONS = ["@PostMapping", "@PutMapping", "@DeleteMapping"]

# Read-only method name patterns that should NOT get @Idempotent
READ_ONLY_METHOD_PREFIXES = [
    "get", "page", "list", "query", "search", "find", "export",
    "download", "preview", "view", "check", "validate", "count",
    "exists", "verify", "lookup", "fetch", "load", "pull", "detail",
    "info", "stat", "report", "summary", "chart", "dashboard",
    "overview", "monitor", "health", "ping", "ready", "rebuild",
    "getByCode", "listByPmId", "getById", "getByProjectCode",
]


def find_java_files(directory: pathlib.Path) -> list[pathlib.Path]:
    """Find all Java files in the directory."""
    return sorted(directory.rglob("*.java"))


def extract_class_name(content: str) -> str | None:
    """Extract the simple class name from the Java file."""
    m = re.search(r'public\s+class\s+(\w+)', content)
    return m.group(1) if m else None


def find_write_methods(content: str) -> list[tuple[int, str]]:
    """
    Find all write methods that need @Idempotent.
    Returns list of (line_number_of_annotation, method_name).
    line_number is 0-based index in the lines array.
    """
    lines = content.split("\n")
    methods = []

    i = 0
    while i < len(lines):
        line = lines[i]

        # Check if this line is a write annotation
        is_write = any(line.strip().startswith(ann) for ann in WRITE_ANNOTATIONS)
        if not is_write:
            i += 1
            continue

        annotation_line = i

        # Check if @Idempotent or @IdempotentExempt is already present
        # Look back up to 15 lines
        has_existing = False
        for j in range(max(0, i - 15), i):
            if "@Idempotent" in lines[j] or "@IdempotentExempt" in lines[j]:
                has_existing = True
                break

        if has_existing:
            i += 1
            continue

        # Find the method name by looking ahead for method declaration
        # The pattern matches: public [generic] returnType methodName(
        method_name = None
        for j in range(i + 1, min(i + 25, len(lines))):
            # Match method declarations like:
            # public BaseResponse<String> save(
            # public BaseResponse<Boolean> update(
            # public Result<XxxVO> create(
            m = re.search(
                r'public\s+(?:<[^>]+>\s+)?(?:\w+(?:\.\w+)*(?:<[^>]*>)?\s+)+(\w+)\s*\(',
                lines[j]
            )
            if not m:
                # Try simpler pattern: public BaseResponse methodName(
                m = re.search(
                    r'public\s+\w+(?:<[^>]*>)?\s+(\w+)\s*\(',
                    lines[j]
                )
            if m:
                method_name = m.group(1)
                break

        if method_name:
            # Check if it's a read-only method
            if method_name in READ_ONLY_METHOD_PREFIXES:
                i += 1
                continue
            if any(method_name.startswith(p) for p in READ_ONLY_METHOD_PREFIXES):
                i += 1
                continue

            methods.append((annotation_line, method_name))

        i += 1

    return methods


def add_idempotent_annotation(
    content: str, class_name: str, methods: list[tuple[int, str]]
) -> str:
    """Add @Idempotent annotations to the methods."""
    lines = content.split("\n")
    # Process methods in reverse order to preserve line numbers
    for line_num, method_name in sorted(methods, reverse=True):
        key = f"ydsz:project:{class_name}:{method_name}:lock"
        idempotent_line = f'    @Idempotent(key = "{key}", ttlSeconds = 5)'
        lines.insert(line_num, idempotent_line)

    return "\n".join(lines)


def add_import(content: str) -> str:
    """Add Idempotent import if not present."""
    if "import com.njydsz.common.lock.annotation.Idempotent" in content:
        return content

    lines = content.split("\n")
    last_import_idx = -1
    for i, line in enumerate(lines):
        if line.strip().startswith("import "):
            last_import_idx = i

    if last_import_idx >= 0:
        lines.insert(last_import_idx + 1, IDEMPOTENT_IMPORT)
    else:
        pkg_idx = -1
        for i, line in enumerate(lines):
            if line.strip().startswith("package "):
                pkg_idx = i
                break
        if pkg_idx >= 0:
            lines.insert(pkg_idx + 2, IDEMPOTENT_IMPORT)

    return "\n".join(lines)


def process_file(filepath: pathlib.Path) -> tuple[bool, list[str]]:
    """Process a single Java file. Returns (changed, list_of_methods)."""
    content = filepath.read_text(encoding="utf-8")

    class_name = extract_class_name(content)
    if not class_name:
        return False, []

    methods = find_write_methods(content)
    if not methods:
        return False, []

    # Add annotations (insert before the @PostMapping/@PutMapping/@DeleteMapping line)
    content = add_idempotent_annotation(content, class_name, methods)

    # Add import
    content = add_import(content)

    filepath.write_text(content, encoding="utf-8")
    return True, [m[1] for m in methods]


def main():
    files = find_java_files(PROJECT_WEB)
    total = len(files)
    changed = 0
    total_methods = 0

    for f in files:
        try:
            file_changed, methods = process_file(f)
            if file_changed:
                changed += 1
                total_methods += len(methods)
                print(f"  {f.name}: {len(methods)} methods -> {', '.join(methods)}")
        except Exception as e:
            print(f"ERROR processing {f.name}: {e}", file=sys.stderr)

    print(f"\nProcessed {total} files, modified {changed} files, added {total_methods} @Idempotent annotations.")


if __name__ == "__main__":
    main()