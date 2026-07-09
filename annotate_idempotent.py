# -*- coding: utf-8 -*-
"""
根据 check-idempotent-coverage.sh 输出的 UNCOVERED 清单，
为缺失 @Idempotent 的写接口补齐 @Idempotent 或 @IdempotentExempt 注解。
"""
import re
from pathlib import Path

BACKEND_DIR = Path("ydsz-pmis-backend")
UNCOVERED_FILE = Path("uncovered.txt")

# 查询语义关键词（方法名子串，忽略大小写）
QUERY_KEYWORDS = [
    "page", "list", "query", "search", "export", "download",
    "preview", "simulate", "previewMigration", "migrateInstances",
]

# 认证 / 会话 / 2FA 相关 Controller
AUTH_SESSION_2FA_CLASSES = {
    "AuthController",
    "SessionController",
    "TwoFactorController",
    "ReAuthController",
}

# 审计清理类方法
AUDIT_CLEANUP_METHODS = {
    ("OperationLogController", "clean"): "审计清理接口，无需幂等",
}

# 定时触发类方法（按 Controller 类 + 方法名）
SCHEDULED_TRIGGER_METHODS = {
    ("InternalJobController", "execute"): "定时触发接口，无需幂等",
    ("InternalJobController", "executeSubTask"): "定时触发接口，无需幂等",
    ("JobController", "trigger"): "定时触发接口，无需幂等",
    ("JobController", "batchTrigger"): "定时触发接口，无需幂等",
    ("JobDagController", "triggerDag"): "定时触发接口，无需幂等",
}


def to_kebab(name: str) -> str:
    # 在大写字母前插入连字符（连续大写也会逐字母插入，符合预期）
    s = re.sub(r"([a-z0-9])([A-Z])", r"\1-\2", name)
    return s.lower().replace("_", "-")


def controller_prefix(class_name: str) -> str:
    if class_name.endswith("Controller"):
        base = class_name[:-len("Controller")]
    else:
        base = class_name
    if not base:
        base = class_name
    return to_kebab(base)


def build_key(class_name: str, method_name: str) -> str:
    return f"{controller_prefix(class_name)}:{to_kebab(method_name)}"


def is_query_semantic(method_name: str) -> bool:
    mn = method_name.lower()
    return any(kw.lower() in mn for kw in QUERY_KEYWORDS)


def classify(class_name: str, method_name: str):
    """返回 (annotation_type, reason_or_none)"""
    if class_name in AUTH_SESSION_2FA_CLASSES:
        return "exempt", "认证/会话/2FA 相关接口，无需幂等"
    key = (class_name, method_name)
    if key in SCHEDULED_TRIGGER_METHODS:
        return "exempt", SCHEDULED_TRIGGER_METHODS[key]
    if key in AUDIT_CLEANUP_METHODS:
        return "exempt", AUDIT_CLEANUP_METHODS[key]
    if is_query_semantic(method_name):
        return "exempt", "查询/导出/预览/模拟语义接口，无需幂等"
    return "idempotent", None


def parse_uncovered(path: Path):
    entries = []
    pattern = re.compile(
        r"^\s*-\s+([A-Za-z0-9_]+)#([A-Za-z0-9_]+)\s+\((POST|PUT|DELETE|PATCH)\s+([^\t]+)\tUNCOVERED\)\s+\[([^\]]+)\]"
    )
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n\r")
            m = pattern.match(line)
            if m:
                entries.append({
                    "class": m.group(1),
                    "method": m.group(2),
                    "http": m.group(3),
                    "path": m.group(4),
                    "module": m.group(5),
                })
    return entries


_CONTROLLER_FILE_MAP = None


def build_controller_map():
    global _CONTROLLER_FILE_MAP
    if _CONTROLLER_FILE_MAP is not None:
        return _CONTROLLER_FILE_MAP
    _CONTROLLER_FILE_MAP = {}
    # Controller 都在 src/main/java 下，缩小扫描范围
    for p in BACKEND_DIR.rglob("*Controller.java"):
        if "/src/main/java/" not in str(p).replace("\\", "/"):
            continue
        name = p.name[:-5]  # .java
        existing = _CONTROLLER_FILE_MAP.get(name)
        if existing is None:
            _CONTROLLER_FILE_MAP[name] = p
        elif "controller" in str(p).lower() and "controller" not in str(existing).lower():
            _CONTROLLER_FILE_MAP[name] = p
    return _CONTROLLER_FILE_MAP


def find_file(class_name: str):
    return build_controller_map().get(class_name)


def insert_imports(lines: list, needs_idempotent: bool, needs_exempt: bool):
    package_idx = -1
    for i, line in enumerate(lines):
        if line.startswith("package "):
            package_idx = i
            break
    if package_idx < 0:
        return

    imports_to_add = []
    if needs_idempotent:
        imports_to_add.append("import com.njydsz.pmis.common.annotation.Idempotent;\n")
    if needs_exempt:
        imports_to_add.append("import com.njydsz.pmis.common.annotation.IdempotentExempt;\n")
    if not imports_to_add:
        return

    # 去重：如果已有对应 import 则跳过
    existing_texts = {line for line in lines if line.startswith("import ")}
    new_imports = [imp for imp in imports_to_add if imp not in existing_texts]
    if not new_imports:
        return

    # 插入位置：package 后的第一个非空行之前，保持其余 import 顺序不变
    insert_pos = package_idx + 1
    while insert_pos < len(lines) and lines[insert_pos].strip() == "":
        insert_pos += 1

    # 如果 package 与现有 import 之间没有空行，则补一个空行
    prefix = ["\n"] if insert_pos == package_idx + 1 or lines[insert_pos - 1].strip() != "" else []
    # 在新 import 后补一个空行，与原有 import 分隔
    suffix = ["\n"] if insert_pos < len(lines) and lines[insert_pos].startswith("import ") else []
    block = prefix + new_imports + suffix
    lines[insert_pos:insert_pos] = block


MAPPING_RE = re.compile(r"^[ \t]*@(PostMapping|PutMapping|DeleteMapping|PatchMapping)\b")
SIG_RE = re.compile(r"^[ \t]*(public|protected|private)[ \t]+[A-Za-z0-9_<>,\[\]\? \t]+[ \t]+([A-Za-z_][A-Za-z0-9_]*)[ \t]*\(")


def process_file(file_path: Path, target_methods: dict):
    """target_methods: method_name -> (class_name, method_name)"""
    text = file_path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    original_lines = lines.copy()

    needs_idempotent = False
    needs_exempt = False
    found_methods = set()

    # 扫描每一行，找 mapping 注解行
    i = 0
    insertions = []  # (insert_index, indent, annotation)
    while i < len(lines):
        line = lines[i]
        if MAPPING_RE.match(line):
            mapping_idx = i
            indent = line[:len(line) - len(line.lstrip())]
            # 向下找方法签名行，跳过注解块及多行注解续行
            j = i + 1
            method_name = None
            while j < len(lines):
                sj = lines[j]
                stripped = sj.strip()
                # 注解行、空行、多行注解续行（含 ) , = 且不像方法签名）均跳过
                if stripped.startswith("@") or stripped == "":
                    j += 1
                    continue
                if (re.search(r"[),=]", stripped) and
                        not re.match(r"(public|private|protected|return|if|for|while|try|catch|throw|new|final|case|switch|break|continue)\b", stripped)):
                    j += 1
                    continue
                m = SIG_RE.match(sj)
                if m:
                    method_name = m.group(2)
                break
            if method_name and method_name in target_methods:
                class_name = target_methods[method_name]["class"]
                ann_type, reason = classify(class_name, method_name)
                if ann_type == "idempotent":
                    key = build_key(class_name, method_name)
                    annotation = f'@Idempotent(key = "{key}", ttlSeconds = 5, message = "请勿重复提交")\n'
                    needs_idempotent = True
                else:
                    # reason 中可能包含引号，简单处理
                    reason_escaped = reason.replace('"', '\\"')
                    annotation = f'@IdempotentExempt("{reason_escaped}")\n'
                    needs_exempt = True
                insertions.append((mapping_idx, indent, annotation))
                found_methods.add(method_name)
            i = j + 1 if method_name else i + 1
        else:
            i += 1

    if not insertions:
        return 0, 0, 0

    # 应用插入（从后往前，避免索引变化）
    insertions.sort(key=lambda x: x[0], reverse=True)
    for idx, indent, ann in insertions:
        lines.insert(idx, indent + ann)

    insert_imports(lines, needs_idempotent, needs_exempt)

    # 写回
    file_path.write_text("".join(lines), encoding="utf-8")

    idempotent_count = sum(1 for _, _, ann in insertions if ann.startswith("@Idempotent("))
    exempt_count = len(insertions) - idempotent_count
    return 1, idempotent_count, exempt_count


def main():
    entries = parse_uncovered(UNCOVERED_FILE)
    if not entries:
        print("未解析到未覆盖写接口，请检查 uncovered.txt 内容")
        return

    # 按类分组
    by_class = {}
    for e in entries:
        by_class.setdefault(e["class"], []).append(e)

    total_controllers = 0
    total_idempotent = 0
    total_exempt = 0
    not_found = []

    for class_name, methods in by_class.items():
        file_path = find_file(class_name)
        if not file_path:
            not_found.append(class_name)
            continue
        target = {m["method"]: m for m in methods}
        modified, idem_cnt, exempt_cnt = process_file(file_path, target)
        if modified:
            total_controllers += 1
            total_idempotent += idem_cnt
            total_exempt += exempt_cnt
            print(f"已修改 {file_path}: +{idem_cnt} @Idempotent, +{exempt_cnt} @IdempotentExempt")

    if not_found:
        print("\n未找到以下类的文件：")
        for c in not_found:
            print(f"  - {c}")

    print(f"\n总计: 修改 {total_controllers} 个 Controller, "
          f"新增 {total_idempotent} 个 @Idempotent, {total_exempt} 个 @IdempotentExempt")


if __name__ == "__main__":
    main()
