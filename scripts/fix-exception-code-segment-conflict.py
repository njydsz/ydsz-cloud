"""P2-4 错误码段位冲突治理

修复两处段位冲突：
1. DocumentExceptionCode: D段位 → G段位（D段位已被 RateLimitExceptionCode 占用）
2. ExcelExceptionCode:   E段位 → H段位（E段位已被 ExternalExceptionCode 占用）

同时在 ExceptionCode.getCategory() 中补充 F/G/H/W 段位映射，
并在 coding-standards.md 中新增 Section 7 文档化段位分配。

约束：使用 UTF-8 编码读写，遵循 prefer-python-over-powershell 规则。
"""

from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path(r"d:/Code/ydsz/ydsz-pmis/ydsz-backend")

DOC_FILE = ROOT / "ydsz-common/ydsz-common-docs/src/main/java/com/njydsz/common/docs/exception/DocumentExceptionCode.java"
EXCEL_FILE = ROOT / "ydsz-common/ydsz-common-excel/src/main/java/com/njydsz/common/excel/exception/ExcelExceptionCode.java"
EXCEPTION_CODE_FILE = ROOT / "ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/enums/ExceptionCode.java"
STANDARDS_FILE = pathlib.Path(r"d:/Code/ydsz/ydsz-pmis/deploy/docs/architecture/coding-standards.md")


def migrate_document_exception_code() -> int:
    """DocumentExceptionCode: D段位 → G段位。

    替换规则（仅作用于本文件内）：
    - 字符串字面量 "Dxxxxx" → "Gxxxxx"
    - Javadoc 中的 D段位 / D01*** / D02*** ... → G段位 / G01*** / G02*** ...

    返回替换次数。
    """
    content = DOC_FILE.read_text(encoding="utf-8")
    original = content

    # 1. 替换字符串字面量中的错误码："Dxxxxx" → "Gxxxxx"
    #    仅匹配 "D" 后跟 5 位数字（错误码格式）
    content = re.sub(r'"(D)(\d{5})"', r'"G\2"', content)

    # 2. 替换 Javadoc 中的段位说明
    content = content.replace("（D 段位 + 五位数字）", "（G 段位 + 五位数字）")
    content = content.replace("D01***", "G01***")
    content = content.replace("D02***", "G02***")
    content = content.replace("D03***", "G03***")
    content = content.replace("D04***", "G04***")
    content = content.replace("D05***", "G05***")
    content = content.replace("D06***", "G06***")
    content = content.replace("D07***", "G07***")
    content = content.replace("D99***", "G99***")

    if content == original:
        return 0
    DOC_FILE.write_text(content, encoding="utf-8")
    return 1


def migrate_excel_exception_code() -> int:
    """ExcelExceptionCode: E段位 → H段位。

    替换规则（仅作用于本文件内）：
    - 字符串字面量 "Exxxxx" → "Hxxxxx"
    - Javadoc 中的 E01xxx / E02xxx / ... → H01xxx / H02xxx / ...

    返回替换次数。
    """
    content = EXCEL_FILE.read_text(encoding="utf-8")
    original = content

    # 1. 替换字符串字面量中的错误码："Exxxxx" → "Hxxxxx"
    content = re.sub(r'"(E)(\d{5})"', r'"H\2"', content)

    # 2. 替换 Javadoc 中的段位说明
    content = content.replace("E01xxx", "H01xxx")
    content = content.replace("E02xxx", "H02xxx")
    content = content.replace("E03xxx", "H03xxx")
    content = content.replace("E04xxx", "H04xxx")

    if content == original:
        return 0
    EXCEL_FILE.write_text(content, encoding="utf-8")
    return 1


def update_exception_code_category() -> int:
    """在 ExceptionCode.getCategory() 中补充 F/G/H/W 段位映射。

    现状：getCategory() 已识别 A/B/C/D/E/S/K/V/I/T/R 前缀，
    但 FileExceptionCode(F)、DocumentExceptionCode(G)、ExcelExceptionCode(H)、
    NextwikiExceptionCode(W) 四个模块的段位未识别，会默认返回 BUSINESS。

    修复：补充 F→INFRASTRUCTURE / G→BUSINESS / H→BUSINESS / W→BUSINESS 映射。
    """
    content = EXCEPTION_CODE_FILE.read_text(encoding="utf-8")
    original = content

    # 在 case 'R' 行之后插入 F/G/H/W 四个新 case
    old_block = "            case 'R':\n                return ExceptionCategory.RATE_LIMIT;\n"
    new_block = (
        "            case 'R':\n"
        "                return ExceptionCategory.RATE_LIMIT;\n"
        "            // 模块专属段位：映射到主分类\n"
        "            case 'F':\n"
        "                // FileExceptionCode - 文件存储属于基础设施异常\n"
        "                return ExceptionCategory.INFRASTRUCTURE;\n"
        "            case 'G':\n"
        "                // DocumentExceptionCode - 文档处理属于业务异常\n"
        "                return ExceptionCategory.BUSINESS;\n"
        "            case 'H':\n"
        "                // ExcelExceptionCode - Excel 处理属于业务异常\n"
        "                return ExceptionCategory.BUSINESS;\n"
        "            case 'W':\n"
        "                // NextwikiExceptionCode - 知识库属于业务异常\n"
        "                return ExceptionCategory.BUSINESS;\n"
    )

    if old_block not in content:
        print("  [WARN] 未找到 ExceptionCode.getCategory() 中的 case 'R' 行，跳过")
        return 0

    content = content.replace(old_block, new_block)

    if content == original:
        return 0
    EXCEPTION_CODE_FILE.write_text(content, encoding="utf-8")
    return 1


def append_section_to_coding_standards() -> int:
    """在 coding-standards.md 末尾追加 Section 7: 错误码段位规范。"""
    content = STANDARDS_FILE.read_text(encoding="utf-8")

    section = """

---

## Section 7: 错误码段位规范

### 7.1 编码格式

所有业务异常码采用 6 位字符串格式：

```
[类型(1位)] + [模块(2位)] + [序号(3位)]
```

- **类型字母**：标识异常大类，决定 `ExceptionCategory` 主分类
- **模块号**：两位数字，标识具体子模块
- **序号**：三位数字，模块内自增序号

### 7.2 主分类段位（5 大主分类，A/B/C/D/E）

| 段位 | 主分类 | 含义 | HTTP 状态码 | 持有枚举 |
|------|--------|------|-------------|----------|
| `A` | BUSINESS | 业务级错误 | 4xx | `UnifiedExceptionCode` |
| `B` | SYSTEM | 系统级错误 | 5xx | `UnifiedExceptionCode`、`UserInfoResultCode` |
| `C` | SECURITY | 安全级错误 | 401/403 | `UnifiedExceptionCode` |
| `D` | RATE_LIMIT | 限流/熔断/降级 | 429/503 | `RateLimitExceptionCode` |
| `E` | EXTERNAL | 外部/三方服务 | 502/504 | `ExternalExceptionCode` |

### 7.3 模块专属段位（F/G/H/W）

模块专属段位是某个公共/业务模块独占的字母前缀，避免与主分类段位冲突。
`ExceptionCode.getCategory()` 将其映射到合适的主分类。

| 段位 | 模块 | 持有枚举 | 主分类映射 |
|------|------|----------|------------|
| `F` | 文件存储 (common-file) | `FileExceptionCode` | INFRASTRUCTURE |
| `G` | 文档处理 (common-docs) | `DocumentExceptionCode` | BUSINESS |
| `H` | Excel 处理 (common-excel) | `ExcelExceptionCode` | BUSINESS |
| `W` | 网盘知识库 (nextwiki) | `NextwikiExceptionCode` | BUSINESS |

### 7.4 段位分配约束

- **禁止段位复用**：同一个字母前缀只能由一个枚举类独占，不允许两个枚举共用 `D01xxx` 区间。
  - 历史冲突已修复：`DocumentExceptionCode` 从 `D` 迁移到 `G`；`ExcelExceptionCode` 从 `E` 迁移到 `H`。
- **新模块段位申请**：新增业务模块需要专属段位时，在本文档登记并更新 `ExceptionCode.getCategory()` 的 switch 分支。
- **主分类段位保留序号 051+**：`UnifiedExceptionCode` 各模块序号从 `051` 起始，避免与已废弃的 `CommExceptionCode` 冲突。
- **模块专属段位从 001 起始**：`F`/`G`/`H`/`W` 等模块专属段位的序号从 `001` 起始。

### 7.5 模块内子段位规划

#### A 段位（业务级，UnifiedExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `A00xxx` | 成功 |
| `A01xxx` | 参数/业务异常 |
| `A02xxx` | 认证异常 |
| `A03xxx` | 权限异常 |
| `A04xxx` | 数据异常 |

#### B 段位（系统级）

| 子段位 | 用途 | 持有枚举 |
|--------|------|----------|
| `B01xxx` | 系统异常 | `UnifiedExceptionCode` |
| `B02xxx` | 外部服务异常 | `UnifiedExceptionCode` |
| `B30xxx` | 用户/认证 | `UserInfoResultCode` |
| `B31xxx` | 组织架构 | `UserInfoResultCode` |
| `B32xxx` | RBAC（角色/权限/菜单/岗位/语言） | `UserInfoResultCode` |

#### D 段位（限流，RateLimitExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `D01xxx` | 全局限流（IP / 用户 / 租户维度） |
| `D02xxx` | 接口粒度限流 |
| `D03xxx` | 热点参数限流 |
| `D04xxx` | 熔断器 |
| `D05xxx` | 服务降级 |
| `D06xxx` | 集群限流 |
| `D07xxx` | 自适应限流 |

#### E 段位（外部服务，ExternalExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `E01xxx` | 通用外部服务 |
| `E02xxx` | Feign / OpenFeign |
| `E03xxx` | 网关 / API Gateway |
| `E04xxx` | 支付服务 |
| `E05xxx` | 短信 / 邮件 / 推送 |
| `E06xxx` | 存储 / OSS / CDN |
| `E07xxx` | 消息队列 |
| `E08xxx` | 搜索引擎 / ES |
| `E09xxx` | 第三方 OAuth |

#### F 段位（文件存储，FileExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `F01xxx` | 文件操作错误 |
| `F02xxx` | 存储桶错误 |
| `F03xxx` | 目录错误 |
| `F04xxx` | 配置错误 |
| `F05xxx` | 私有链接错误 |
| `F06xxx` | 范围下载错误 |
| `F07xxx` | 分片上传错误 |
| `F99xxx` | 未知错误（兜底） |

#### G 段位（文档处理，DocumentExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `G01xxx` | 解析错误 |
| `G02xxx` | 预处理错误 |
| `G03xxx` | 安全扫描错误 |
| `G04xxx` | PII 检测错误 |
| `G05xxx` | 脱敏错误 |
| `G06xxx` | 水印错误 |
| `G07xxx` | 转换错误 |
| `G99xxx` | 未知错误（兜底） |

#### H 段位（Excel 处理，ExcelExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `H01xxx` | 读取异常 |
| `H02xxx` | 写入异常 |
| `H03xxx` | 转换异常 |
| `H04xxx` | 配置异常 |

#### W 段位（网盘知识库，NextwikiExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `W01xxx` | 文件操作错误 |
| `W02xxx` | 版本错误 |
| `W03xxx` | 分享错误 |
| `W04xxx` | 配额错误 |
| `W05xxx` | 权限错误 |
| `W06xxx` | 回收站错误 |
| `W07xxx` | 标签错误 |
| `W08xxx` | 预览错误 |
| `W09xxx` | 系统错误 |

### 7.6 注册规范

- 所有 `ExceptionCode` 实现类**必须**在静态块中通过 `ExceptionCodeRegistry.register(map)` 完成注册，否则 `ExceptionCode.fromCode(code)` 无法反查。
- 重复注册时默认宽松模式（保留首次注册值 + warn 日志）；如需 fail-fast，使用 `registerStrict(map)`。
- **段位冲突检测**：CI 可通过 `ExceptionCodeRegistry.allRegistered()` 反向扫描所有已注册 code，发现同 code 跨枚举类即报警。

### 7.7 相关文件

- [UnifiedExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/code/UnifiedExceptionCode.java)
- [ExternalExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/code/ExternalExceptionCode.java)
- [RateLimitExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/code/RateLimitExceptionCode.java)
- [DocumentExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-docs/src/main/java/com/njydsz/common/docs/exception/DocumentExceptionCode.java)
- [ExcelExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-excel/src/main/java/com/njydsz/common/excel/exception/ExcelExceptionCode.java)
- [FileExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-file/src/main/java/com/njydsz/common/file/exception/FileExceptionCode.java)
- [NextwikiExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-nextwiki/ydsz-nextwiki-domain/src/main/java/com/njydsz/nextwiki/domain/enums/NextwikiExceptionCode.java)
- [UserInfoResultCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/enums/UserInfoResultCode.java)
- [ExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/enums/ExceptionCode.java)
- [ExceptionCategory.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/enums/ExceptionCategory.java)
"""

    if "## Section 7" in content:
        # 已存在则跳过
        return 0

    new_content = content.rstrip() + "\n" + section
    STANDARDS_FILE.write_text(new_content, encoding="utf-8")
    return 1


def main() -> None:
    print("=== P2-4 错误码段位冲突治理 ===")

    print("\n[1/4] 迁移 DocumentExceptionCode: D段位 → G段位")
    n = migrate_document_exception_code()
    print(f"  完成，文件修改数: {n}")

    print("\n[2/4] 迁移 ExcelExceptionCode: E段位 → H段位")
    n = migrate_excel_exception_code()
    print(f"  完成，文件修改数: {n}")

    print("\n[3/4] 更新 ExceptionCode.getCategory() 补充 F/G/H/W 段位映射")
    n = update_exception_code_category()
    print(f"  完成，文件修改数: {n}")

    print("\n[4/4] 在 coding-standards.md 追加 Section 7: 错误码段位规范")
    n = append_section_to_coding_standards()
    print(f"  完成，文件修改数: {n}")

    print("\n=== 治理完成 ===")
    print("后续步骤：")
    print("  1. 执行 mvn compile 验证后端编译")
    print("  2. 抽查 DocumentExceptionCode / ExcelExceptionCode / ExceptionCode 文件内容")
    print("  3. 确认 coding-standards.md Section 7 渲染正常")


if __name__ == "__main__":
    main()
