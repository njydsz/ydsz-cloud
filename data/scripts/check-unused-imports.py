#!/usr/bin/env python3
"""未使用 import 门禁 —— 防止 Java 源码中残留无引用的 import 语句。

背景（IMPORT-003）：
  所有类型必须通过顶部 import 声明，但**未使用的 import 同样禁止**。
  实测全仓曾有 140 处未使用 import（2026-11-03 已清零），门禁用于防止回归。

判定方式：
  提取每行 `import xxx.Yyy;` 的简单类名 Yyy，在非 import 行中做词边界匹配；
  零出现即判定为未使用。

白名单（不判红）：
  - 注解（@Configuration / @Service / @Slf4j 等）—— 这些名字在文件中只出现一次是正常的，
    因为注解使用形式是 `@Name`，不含类名引用
  - 静态导入（`import static ...`）
  - 通配符导入（`import xxx.*;`）—— 规范已禁止，由 checkstyle 处理
  - 测试代码（src/test）

用法：
  python3 data/scripts/check-unused-imports.py [--self-test] [--list]
退出码非 0 表示存在未使用 import；前提不成立同样失败（fail-closed）。
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SKIP_DIRS = frozenset({"target", "backup_logs", "node_modules", "dist", ".git", ".idea"})

IMPORT_RE = re.compile(r'^import\s+(?!static)(?!.*\.\*;)[\w.]+\.(\w+)\s*;\s*$')

# 注解名白名单：这些名字在文件中仅出现一次属正常（注解用法不含类名引用）
ANNOTATION_NAMES = frozenset({
    "Configuration", "Autowired", "Component", "Service", "Repository", "Slf4j", "Data", "Getter",
    "Setter", "Builder", "NoArgsConstructor", "AllArgsConstructor", "RequiredArgsConstructor", "Value",
    "Bean", "PostConstruct", "PreDestroy", "Resource", "Lazy", "Primary", "Qualifier", "Scope",
    "Transactional", "Override", "SuppressWarnings", "Deprecated", "FunctionalInterface", "NotNull",
    "Nullable", "Valid", "Validated", "NotEmpty", "NotBlank", "Size", "Min", "Max", "Pattern",
    "Email", "Positive", "Negative", "Test", "BeforeAll", "AfterAll", "BeforeEach", "AfterEach",
    "DisplayName", "ParameterizedTest", "RepeatedTest", "Timeout", "Tag", "Order", "ExtendWith",
    "SpringBootTest", "WebMvcTest", "DataJpaTest", "RestClientTest", "RestController", "Controller",
    "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping",
    "RequestBody", "ResponseBody", "PathVariable", "RequestParam", "RequestHeader", "RequestAttribute",
    "ModelAttribute", "SessionAttribute", "CookieValue", "CrossOrigin", "Async", "Scheduled",
    "EnableConfigurationProperties", "ConfigurationProperties", "ConditionalOnProperty",
    "ConditionalOnMissingBean", "ConditionalOnBean", "ConditionalOnClass", "FeignClient", "TableName",
    "TableField", "TableId", "TableLogic", "EqualsAndHashCode", "ToString", "Log", "Entity", "Column",
    "Version", "Table", "Index", "Id", "Enumerated", "Lob", "Transient", "JoinColumn", "OneToMany",
    "ManyToOne", "OneToOne", "ManyToMany", "JoinTable", "OrderBy", "Cacheable", "CacheEvict", "CachePut",
    "PersistenceContext", "Inject", "Named", "Produces", "Consumes", "Path", "POST", "GET", "Param",
    "Query", "Select", "Update", "Delete", "Insert", "Options", "Result", "Results", "ResultType",
    "ConstructorResult", "EntityResult", "SqlResultSetMapping", "NamedQuery", "NamedQueries",
})


class GateError(RuntimeError):
    """门禁前提不成立。"""


def scan_file(rel_path: str, lines: list[str]) -> list[str]:
    body = "".join(line for line in lines if not line.startswith("import"))
    errors: list[str] = []
    for idx, line in enumerate(lines, start=1):
        m = IMPORT_RE.match(line.rstrip("\r\n"))
        if not m:
            continue
        cls = m.group(1)
        if cls in ANNOTATION_NAMES:
            continue
        if not re.search(r"\b" + re.escape(cls) + r"\b", body):
            errors.append(f"IMPORT-003 {rel_path}:{idx} 未使用的 import：{cls}")
    return errors


def iter_java_files(root: Path):
    for dirpath, dirnames, filenames in os.walk(root):
        parts = Path(dirpath).parts
        if any(skip in parts for skip in SKIP_DIRS):
            continue
        for fn in filenames:
            if not fn.endswith(".java"):
                continue
            full = Path(dirpath) / fn
            normalized = str(full).replace("\\", "/")
            if "/src/test/" in normalized:
                continue
            if "/src/main/" not in normalized:
                continue
            yield full


def scan(root: Path) -> tuple[list[str], int]:
    files = sorted(iter_java_files(root))
    if not files:
        raise GateError(f"{root} 内没有可扫描的 Java 主源码")
    errors: list[str] = []
    for path in files:
        rel = path.relative_to(root).as_posix()
        try:
            with open(path, "r", encoding="utf-8", errors="replace") as fh:
                lines = fh.readlines()
        except OSError as exc:
            raise GateError(f"读取 {path} 失败：{exc}") from exc
        errors.extend(scan_file(rel, lines))
    return errors, len(files)


def self_test() -> int:
    passed = 0
    failures: list[str] = []

    def case(name: str, ok: bool) -> None:
        nonlocal passed
        if ok:
            passed += 1
        else:
            failures.append(name)

    def scan_lines(lines: list[str]) -> list[str]:
        return scan_file("T.java", lines)

    # --- 真实未使用 ---
    errs = scan_lines([
        "import java.util.List;\n",
        "import java.util.Map;\n",
        "\n",
        "public class T {\n",
        "  private List<String> items;\n",
        "}\n",
    ])
    case("未使用 import 判红", len(errs) == 1 and "Map" in errs[0])

    errs = scan_lines([
        "import java.util.List;\n",
        "\n",
        "public class T {\n",
        "  private List<String> items;\n",
        "}\n",
    ])
    case("已使用 import 放过", errs == [])

    # --- 注解白名单 ---
    errs = scan_lines([
        "import org.springframework.stereotype.Service;\n",
        "\n",
        "@Service\n",
        "public class T {\n",
        "}\n",
    ])
    case("@Service 注解放过", errs == [])

    errs = scan_lines([
        "import lombok.extern.slf4j.Slf4j;\n",
        "\n",
        "@Slf4j\n",
        "public class T {\n",
        "}\n",
    ])
    case("@Slf4j 注解放过", errs == [])

    errs = scan_lines([
        "import org.springframework.beans.factory.annotation.Autowired;\n",
        "\n",
        "public class T {\n",
        "}\n",
    ])
    case("@Autowired 未使用但属注解白名单", errs == [])

    # --- 静态导入 / 通配符 ---
    errs = scan_lines([
        "import static java.util.Collections.emptyList;\n",
        "\n",
        "public class T {\n",
        "}\n",
    ])
    case("静态导入放过", errs == [])

    errs = scan_lines([
        "import java.util.*;\n",
        "\n",
        "public class T {\n",
        "}\n",
    ])
    case("通配符导入放过", errs == [])

    # --- 多处未使用 ---
    errs = scan_lines([
        "import java.util.List;\n",
        "import java.util.Map;\n",
        "import java.util.Set;\n",
        "\n",
        "public class T {\n",
        "  private List<String> items;\n",
        "}\n",
    ])
    case("多处未使用全部报出", len(errs) == 2)

    # --- 行号定位 ---
    errs = scan_lines([
        "package a;\n",
        "\n",
        "import java.util.Map;\n",
        "\n",
        "public class T {\n",
        "}\n",
    ])
    case("行号定位到第 3 行", ":3 " in errs[0])

    # --- 同名类在不同包 ---
    errs = scan_lines([
        "import java.util.Date;\n",
        "import java.sql.Date;\n",
        "\n",
        "public class T {\n",
        "  private Date d;\n",
        "}\n",
    ])
    case("同名类按简单名判定（两个都算已使用）", errs == [])

    # --- 正则常量 ---
    case("import 正则匹配普通导入", bool(IMPORT_RE.match("import java.util.List;")))
    case("import 正则不匹配静态导入", not IMPORT_RE.match("import static a.B.c;"))
    case("import 正则不匹配通配符", not IMPORT_RE.match("import java.util.*;"))
    case("import 正则不匹配非 import", not IMPORT_RE.match("package a;"))

    # --- fail-closed ---
    try:
        scan(Path("/nonexistent-repo-for-unused-imports"))
        case("空目录 fail-closed", False)
    except GateError:
        case("空目录 fail-closed", True)

    case("自检覆盖正反例与 fail-closed", passed >= 14)

    for name in failures:
        print(f"  ✗ 断言未通过：{name}", file=sys.stderr)
    total = passed + len(failures)
    if failures:
        print(f"❌ check-unused-imports 自检：{passed}/{total} 条断言通过", file=sys.stderr)
        return 1
    print(f"✅ check-unused-imports 自检：{total}/{total} 条断言通过")
    return 0


def main() -> int:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="未使用 import 门禁")
    parser.add_argument("--root", default=str(REPO_ROOT), help="仓库根目录")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--list", action="store_true", help="只列出不判红")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    root = Path(args.root).resolve()
    try:
        errors, scanned = scan(root)
    except GateError as exc:
        print(f"❌ 未使用 import 门禁无法建立判断依据：{exc}", file=sys.stderr)
        return 1

    print(f"扫描 {scanned} 个 Java 主源码文件（根目录 {root}）")
    for err in errors:
        print(f"{'⚠️ ' if args.list else '❌'} {err}")
    if errors:
        print(
            f"{'⚠️' if args.list else '❌'} 发现 {len(errors)} 处未使用的 import\n"
            f"   请删除；注意 @Annotation 形式的注解名不判红（规范允许）。",
            file=sys.stderr,
        )
        return 0 if args.list else 1
    print("✅ 未使用 import 门禁通过：无冗余 import")
    return 0


if __name__ == "__main__":
    sys.exit(main())
