"""从根 pom.xml 中移除所有 sales/finance 模块声明和 dependencyManagement 条目

移除内容:
  1. <module>ydsz-pmis-sales</module>
  2. <module>ydsz-pmis-finance</module>
  3. dependencyManagement 中的 ydsz-pmis-sales(-api/domain/infra/server/web) 依赖
  4. dependencyManagement 中的 ydsz-pmis-finance(-api/domain/infra/server/web) 依赖
"""

from __future__ import annotations

import pathlib
import re

POM_PATH = pathlib.Path("ydsz-pmis-backend/pom.xml")

# 需要移除的模块名
MODULES_TO_REMOVE = [
    "ydsz-pmis-sales",
    "ydsz-pmis-sales-api",
    "ydsz-pmis-sales-domain",
    "ydsz-pmis-sales-infra",
    "ydsz-pmis-sales-server",
    "ydsz-pmis-sales-web",
    "ydsz-pmis-finance",
    "ydsz-pmis-finance-api",
    "ydsz-pmis-finance-domain",
    "ydsz-pmis-finance-infra",
    "ydsz-pmis-finance-server",
    "ydsz-pmis-finance-web",
]


def remove_module_declarations(content: str) -> tuple[str, int]:
    """移除 <module>ydsz-pmis-sales</module> 等声明."""
    count = 0
    for mod in MODULES_TO_REMOVE:
        pattern = re.compile(rf"^\s*<module>{re.escape(mod)}</module>\s*\n", re.MULTILINE)
        new_content, n = pattern.subn("", content)
        if n > 0:
            count += n
            content = new_content
    return content, count


def remove_dependency_mgmt(content: str) -> tuple[str, int]:
    """移除 dependencyManagement 中的 sales/finance 依赖块.

    每个依赖块形如:
        <dependency>
            <groupId>com.njydsz.pmis</groupId>
            <artifactId>ydsz-pmis-sales-xxx</artifactId>
            <version>${project.version}</version>
        </dependency>
    """
    count = 0
    for mod in MODULES_TO_REMOVE:
        # 匹配整个 <dependency>...</dependency> 块,包含 artifactId 为 mod 的
        pattern = re.compile(
            rf"\s*<dependency>\s*\n"
            rf"\s*<groupId>com\.njydsz\.pmis</groupId>\s*\n"
            rf"\s*<artifactId>{re.escape(mod)}</artifactId>\s*\n"
            rf"\s*<version>\$\{{project\.version\}}</version>\s*\n"
            rf"\s*</dependency>\s*\n",
            re.MULTILINE,
        )
        new_content, n = pattern.subn("", content)
        if n > 0:
            count += n
            content = new_content
    return content, count


def main() -> None:
    if not POM_PATH.exists():
        raise SystemExit(f"pom.xml 不存在: {POM_PATH}")

    content = POM_PATH.read_text(encoding="utf-8")

    content, n1 = remove_module_declarations(content)
    print(f"[1] 移除 <module> 声明: {n1} 处")

    content, n2 = remove_dependency_mgmt(content)
    print(f"[2] 移除 dependencyManagement 条目: {n2} 处")

    POM_PATH.write_text(content, encoding="utf-8")
    print(f"\n[DONE] 共移除 {n1 + n2} 处 sales/finance 引用")


if __name__ == "__main__":
    main()
