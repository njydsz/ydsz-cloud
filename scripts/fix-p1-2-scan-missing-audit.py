"""
P1-2 识别关键业务 Controller 中"含写操作但缺 @Audit"的清单。

判定规则：
- 含 @PostMapping / @PutMapping / @DeleteMapping / @PatchMapping 任意一个
- 但方法上没有 @Audit 注解

输出：每个 Controller 列出缺 @Audit 的写方法签名
"""
import pathlib
import re

ROOT = pathlib.Path("d:/Code/ydsz/ydsz-pmis/ydsz-backend")

WRITE_MAPPING_PATTERN = re.compile(
    r"@(PostMapping|PutMapping|DeleteMapping|PatchMapping)"
)
AUDIT_PATTERN = re.compile(r"@Audit\b")
METHOD_SIGNATURE_PATTERN = re.compile(
    r"^\s*(public\s+\S+\s+\w+\s*\([^)]*\))\s*\{?\s*$"
)
CLASS_DECL_PATTERN = re.compile(r"^\s*public\s+class\s+(\w+)")

WRITE_ANNOTATIONS = ("PostMapping", "PutMapping", "DeleteMapping", "PatchMapping")


def scan_controller(path: pathlib.Path) -> list[tuple[str, str]]:
    """扫描 Controller 文件，返回 [(methodName, lineNum), ...] 缺 @Audit 的写方法"""
    content = path.read_text(encoding="utf-8")
    lines = content.split("\n")

    missing_methods: list[tuple[str, int]] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        # 找写映射注解
        wm_match = WRITE_MAPPING_PATTERN.search(line)
        if wm_match:
            # 向下扫描 15 行内是否有 @Audit
            has_audit = False
            method_sig_line = None
            for j in range(i + 1, min(i + 20, len(lines))):
                if AUDIT_PATTERN.search(lines[j]):
                    has_audit = True
                    break
                sig_match = METHOD_SIGNATURE_PATTERN.match(lines[j])
                if sig_match:
                    method_sig_line = sig_match.group(1)
                    break
            # 向上扫描 5 行（注解可能在方法上方）
            if not has_audit:
                for j in range(max(0, i - 5), i):
                    if AUDIT_PATTERN.search(lines[j]):
                        has_audit = True
                        break
            if not has_audit and method_sig_line:
                missing_methods.append((method_sig_line, i + 1))
        i += 1

    return missing_methods


def main():
    print("=" * 80)
    print("P1-2 关键业务 Controller 缺 @Audit 写方法清单")
    print("=" * 80)

    total_missing = 0
    total_files_with_missing = 0

    for path in ROOT.rglob("*Controller.java"):
        # 仅处理 web 层
        if "ydsz-" not in path.name and "Controller" not in path.name:
            continue
        parts = path.parts
        if "ydsz-common" in parts:
            continue
        if "ydsz-backend" not in parts:
            continue
        # 仅处理 web 层 Controller（ydsz-*-web 子模块）
        if "ydsz-web" not in parts:
            continue

        try:
            missing = scan_controller(path)
        except Exception as e:
            print(f"[ERROR] {path.name}: {e}")
            continue

        if missing:
            total_files_with_missing += 1
            rel = path.relative_to(ROOT)
            print(f"\n[FILE] {rel}")
            for sig, line_no in missing:
                total_missing += 1
                print(f"  L{line_no}: {sig.strip()}")

    print("\n" + "=" * 80)
    print(f"Total controllers with missing @Audit: {total_files_with_missing}")
    print(f"Total write methods missing @Audit:    {total_missing}")
    print("=" * 80)


if __name__ == "__main__":
    main()
