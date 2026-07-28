"""
P0 整改：为 ydsz-cronjob 模块缺失 @Audit 的 Controller 添加审计注解。

策略：
1. 扫描 cronjob-web 下所有 Controller 类
2. 对已有 @Idempotent 但缺少 @Audit 的写方法添加 @Audit
3. 自动添加 import 语句
"""

import pathlib
import re
import sys

CRONJOB_WEB = pathlib.Path(
    r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-cronjob\ydsz-cronjob-web\src\main\java\com\njydsz\cronjob\web\controller"
)

AUDIT_IMPORT = "import com.njydsz.common.audit.annotation.Audit;"
AUDIT_ACTION_IMPORT = "import com.njydsz.common.audit.enums.AuditAction;"
AUDIT_TYPE_IMPORT = "import com.njydsz.common.audit.enums.AuditType;"

WRITE_ANNOTATIONS = ["@PostMapping", "@PutMapping", "@DeleteMapping"]

# Map from method name to Chinese audit description
AUDIT_CONTENT_MAP = {
    "save": "CREATE",
    "create": "CREATE",
    "update": "UPDATE",
    "remove": "DELETE",
    "delete": "DELETE",
    "pause": "UPDATE",
    "resume": "UPDATE",
    "cancel": "UPDATE",
    "retry": "UPDATE",
    "retryNode": "UPDATE",
    "rollback": "UPDATE",
    "test": "OTHER",
    "import": "IMPORT",
    "export": "EXPORT",
    "context": "UPDATE",
}

# Method name to module description mapping
MODULE_MAP = {
    "DagInstanceControl": "DAG实例控制",
    "Connector": "连接器管理",
    "JobDagInstance": "DAG实例管理",
    "JobHistory": "任务历史",
    "Job": "任务管理",
    "JobGroup": "任务分组",
    "JobWebhook": "Webhook管理",
    "Alert": "告警管理",
    "GlueCode": "脚本管理",
    "JobDag": "DAG定义",
}


def find_java_files(directory: pathlib.Path) -> list[pathlib.Path]:
    return sorted(directory.rglob("*.java"))


def extract_class_name(content: str) -> str | None:
    m = re.search(r'public\s+class\s+(\w+)', content)
    return m.group(1) if m else None


def find_audit_missing_methods(content: str) -> list[tuple[int, str, str]]:
    """
    Find write methods that have @Idempotent but are missing @Audit.
    Returns list of (insert_line_number, method_name, audit_action).
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

        # Check if @Audit is already present (look back up to 15 lines)
        has_audit = False
        for j in range(max(0, i - 15), i + 1):
            if "@Audit" in lines[j]:
                has_audit = True
                break

        if has_audit:
            i += 1
            continue

        # Find the method name
        method_name = None
        for j in range(i + 1, min(i + 25, len(lines))):
            m = re.search(
                r'public\s+\w+(?:<[^>]*>)?\s+(\w+)\s*\(',
                lines[j]
            )
            if m:
                method_name = m.group(1)
                break

        if method_name:
            action = AUDIT_CONTENT_MAP.get(method_name, "OTHER")
            # Insert @Audit AFTER the @PostMapping/@PutMapping/@DeleteMapping line
            methods.append((i + 1, method_name, action))

        i += 1

    return methods


def add_audit_annotation(
    content: str, class_name: str, methods: list[tuple[int, str, str]]
) -> str:
    """Add @Audit annotations to the methods."""
    lines = content.split("\n")

    # Determine module name from class name
    module = None
    for key, val in MODULE_MAP.items():
        if key in class_name:
            module = val
            break
    if not module:
        module = class_name.replace("Controller", "")

    # Process methods in reverse order to preserve line numbers
    for line_num, method_name, action in sorted(methods, reverse=True):
        audit_line = f'    @Audit(module = "{module}", type = AuditType.OPERATION, action = AuditAction.{action}, content = "\'{method_name}\'")'
        lines.insert(line_num, audit_line)

    return "\n".join(lines)


def add_imports(content: str) -> str:
    """Add Audit imports if not present."""
    lines = content.split("\n")
    needed_imports = []

    if "import com.njydsz.common.audit.annotation.Audit;" not in content:
        needed_imports.append(AUDIT_IMPORT)
    if "import com.njydsz.common.audit.enums.AuditAction;" not in content:
        needed_imports.append(AUDIT_ACTION_IMPORT)
    if "import com.njydsz.common.audit.enums.AuditType;" not in content:
        needed_imports.append(AUDIT_TYPE_IMPORT)

    if not needed_imports:
        return content

    # Find the last import statement
    last_import_idx = -1
    for i, line in enumerate(lines):
        if line.strip().startswith("import "):
            last_import_idx = i

    if last_import_idx >= 0:
        for idx, imp in enumerate(needed_imports):
            lines.insert(last_import_idx + 1 + idx, imp)
    else:
        pkg_idx = -1
        for i, line in enumerate(lines):
            if line.strip().startswith("package "):
                pkg_idx = i
                break
        if pkg_idx >= 0:
            for idx, imp in enumerate(needed_imports):
                lines.insert(pkg_idx + 2 + idx, imp)

    return "\n".join(lines)


def process_file(filepath: pathlib.Path) -> tuple[bool, list[str]]:
    content = filepath.read_text(encoding="utf-8")

    class_name = extract_class_name(content)
    if not class_name:
        return False, []

    methods = find_audit_missing_methods(content)
    if not methods:
        return False, []

    content = add_audit_annotation(content, class_name, methods)
    content = add_imports(content)

    filepath.write_text(content, encoding="utf-8")
    return True, [m[1] for m in methods]


def main():
    files = find_java_files(CRONJOB_WEB)
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

    print(f"\nProcessed {total} files, modified {changed} files, added {total_methods} @Audit annotations.")


if __name__ == "__main__":
    main()