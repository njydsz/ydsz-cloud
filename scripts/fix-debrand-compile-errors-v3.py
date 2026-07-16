#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""修复剩余编译错误 - 第三批。

1. ExcelHealthIndicator - Spring Boot 4.x 包路径变更
2. IndexRebuildService - rebuildAll 方法缺少关闭的 }
3. OpenAICompatibleClient - getIntValue 方法签名不匹配
4. ydsz-nextwiki-infra - 缺少 mybatis-plus 依赖
"""

import pathlib

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis")


def fix_excel_health_indicator():
    """修复 ExcelHealthIndicator - Spring Boot 4.x 包路径变更。

    旧: org.springframework.boot.actuate.health.Health
    新: org.springframework.boot.health.contributor.Health
    """
    f = ROOT / "ydsz-backend/ydsz-common/ydsz-common-excel/src/main/java/com/njydsz/common/excel/spring/boot/ExcelHealthIndicator.java"
    content = f.read_text(encoding="utf-8")

    old_imports = """import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;"""

    new_imports = """import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;"""

    if old_imports in content:
        content = content.replace(old_imports, new_imports, 1)
        f.write_text(content, encoding="utf-8")
        print(f"[OK] {f.name}: 修复 Spring Boot 4.x 包路径")
    else:
        print(f"[SKIP] {f.name}: 未找到旧 import")


def fix_index_rebuild_service():
    """修复 IndexRebuildService - rebuildAll 方法缺少关闭的 }。

    finally 块之后缺少 } 来关闭 rebuildAll 方法。
    """
    f = ROOT / "ydsz-backend/ydsz-common/ydsz-common-search/src/main/java/com/njydsz/common/search/service/IndexRebuildService.java"
    content = f.read_text(encoding="utf-8")

    # 在 finally 块之后添加 } 来关闭 rebuildAll 方法
    old = """        } finally {
            rebuilding = false;
        }

    // P0-6: async rebuild using managed thread"""

    new = """        } finally {
            rebuilding = false;
        }
    }

    // P0-6: async rebuild using managed thread"""

    if old in content:
        content = content.replace(old, new, 1)
        f.write_text(content, encoding="utf-8")
        print(f"[OK] {f.name}: 添加 rebuildAll 方法关闭的 }}")
    else:
        print(f"[SKIP] {f.name}: 未找到目标模式")


def fix_openai_compatible_client_getintvalue():
    """修复 OpenAICompatibleClient - getIntValue 方法签名不匹配。

    JsonObject.getIntValue(String key) 只接受一个参数，不接受默认值。
    修改 getIntValue("key", 0) 为 getIntValue("key")。
    """
    f = ROOT / "ydsz-backend/ydsz-agent/ydsz-agent-infra/src/main/java/com/njydsz/agent/infra/llm/OpenAICompatibleClient.java"
    content = f.read_text(encoding="utf-8")

    # getIntValue("prompt_tokens", 0) → getIntValue("prompt_tokens")
    # getIntValue("completion_tokens", 0) → getIntValue("completion_tokens")
    fixes = [
        ('getIntValue("prompt_tokens", 0)', 'getIntValue("prompt_tokens")'),
        ('getIntValue("completion_tokens", 0)', 'getIntValue("completion_tokens")'),
    ]

    count = 0
    for old, new in fixes:
        if old in content:
            content = content.replace(old, new)
            count += content.count(new) - content.count(old)  # 粗略计数

    # 简单计数
    count = 0
    for old, new in fixes:
        c = content.count(new)
        # 检查是否还有 old
        if old not in content:
            count += 1

    f.write_text(content, encoding="utf-8")
    print(f"[OK] {f.name}: 修改 getIntValue 方法调用")


def fix_nextwiki_infra_pom():
    """修复 ydsz-nextwiki-infra - 缺少 mybatis-plus 依赖。"""
    f = ROOT / "ydsz-backend/ydsz-nextwiki/ydsz-nextwiki-infra/pom.xml"
    content = f.read_text(encoding="utf-8")

    # 检查是否已有 mybatis-plus 依赖
    if "mybatis-plus" in content:
        print(f"[SKIP] {f.name}: 已有 mybatis-plus 依赖")
        return

    # 在 </dependencies> 前添加依赖
    old = """    </dependencies>"""

    new = """        <!-- MyBatis Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis-spring</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-tx</artifactId>
        </dependency>
    </dependencies>"""

    if old in content:
        content = content.replace(old, new, 1)
        f.write_text(content, encoding="utf-8")
        print(f"[OK] {f.name}: 添加 mybatis-plus 依赖")
    else:
        print(f"[SKIP] {f.name}: 未找到 </dependencies>")


def main():
    print("=== 修复剩余编译错误 (第三批) ===\n")
    fix_excel_health_indicator()
    fix_index_rebuild_service()
    fix_openai_compatible_client_getintvalue()
    fix_nextwiki_infra_pom()
    print("\n=== 完成 ===")


if __name__ == "__main__":
    main()
