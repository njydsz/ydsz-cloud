<!--
  ===========================================================================
  文件名: rule-pack-market.md
  路径:   docs/rules/rule-pack-market.md
  作用:   LiteRule 1.4.0+ 规则集市场（RulePack）元数据设计目标、数据结构、使用示例
  关联:   ydsz-pmis-literule 源码  /  rule-template-spi.md  /  rule-conflict-detection.md
  ===========================================================================
-->

# 规则集市场元数据

> 适用于 1.4.0 起。LiteRule 定义 `RulePack` 规则集元数据，将一组相关规则打包发布到规则集市场，支持按行业、标签检索，按下载量、评分排序，用户可一键导入整包规则，实现跨项目、跨租户的规则复用与最佳实践共享。

## 1. 设计目标

- **聚合复用**：将多条相关规则（如"金融行业风险预警规则集"包含 EVM、利润率、利用率等规则）打包为一个 `RulePack`，支持一键导入整包
- **版本管理**：规则集本身有独立 `packVersion`（语义化版本），支持升级与回滚，与单个 `RuleDefinition.version` 解耦
- **市场检索**：提供 `industry`（适用行业）、`tags`（标签）、`downloadCount`（下载量）、`rating`（评分）四个检索维度，便于按行业筛选、按热度排序
- **作者归属**：`author` 字段标注发布方，支持官方运营团队与社区贡献者发布的规则集
- **解耦引用**：`ruleCodes` 仅引用 `RuleDefinition.code`，规则集本身不持有规则定义副本，规则升级时规则集自动反映最新版本
- **与模板互补**：`RuleTemplateProvider`（P2-5）面向单条模板导入，`RulePack` 面向多条规则聚合导入，两者互补

## 2. 核心类

| 类 | 路径 | 职责 |
|----|------|------|
| `RulePack` | `literule.api` | 规则集元数据 DTO，描述一个可发布的规则包 |
| `RuleDefinition` | `literule.api` | 单条规则定义，`RulePack.ruleCodes` 引用其 `code` |
| `RuleTemplateProvider` | `literule.spi` | 规则模板提供者（P2-5），未来支持从 `RulePack` 批量导入模板 |

## 3. 数据结构

### 3.1 `RulePack`

```java
@Data
@Builder
public class RulePack implements Serializable {
    private String packCode;         // 规则集编码（唯一）
    private String packName;         // 规则集名称
    private String packVersion;      // 规则集版本号（语义化版本，如 1.0.0）
    private String description;      // 规则集描述
    private String industry;         // 适用行业编码
    private List<String> tags;       // 标签列表（用于市场筛选与检索）
    private List<String> ruleCodes;  // 包含的规则编码列表（引用 RuleDefinition.code）
    private String author;           // 作者（发布方）
    private long downloadCount;      // 下载次数（市场热度排序依据）
    private double rating;           // 评分（0.0 ~ 5.0，市场质量排序依据）
}
```

### 3.2 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `packCode` | `String` | 是 | 规则集编码，全局唯一，如 `PACK-FINANCE-RISK-V1` |
| `packName` | `String` | 是 | 规则集显示名称，如"金融行业风险预警规则集" |
| `packVersion` | `String` | 是 | 语义化版本号，如 `1.0.0`、`1.1.0-beta` |
| `description` | `String` | 否 | 规则集描述，说明适用场景与覆盖的风险类型 |
| `industry` | `String` | 否 | 适用行业编码，如 `FINANCE` / `MANUFACTURING` / `IT` |
| `tags` | `List<String>` | 否 | 标签列表，如 `["风险预警", "EVM", "利润率"]` |
| `ruleCodes` | `List<String>` | 是 | 包含的规则编码列表，引用 `RuleDefinition.code` |
| `author` | `String` | 否 | 作者或发布方，如"ydsz-pmis-team" |
| `downloadCount` | `long` | 否 | 下载次数，默认 0，市场排序依据 |
| `rating` | `double` | 否 | 评分 0.0~5.0，默认 0.0，市场质量排序依据 |

## 4. 使用示例

### 4.1 构造规则集

```java
RulePack financeRiskPack = RulePack.builder()
        .packCode("PACK-FINANCE-RISK-V1")
        .packName("金融行业风险预警规则集")
        .packVersion("1.0.0")
        .description("覆盖 EVM 红色项目、利润率预警、利用率不足等金融行业典型风险场景")
        .industry("FINANCE")
        .tags(List.of("风险预警", "EVM", "利润率", "利用率"))
        .ruleCodes(List.of(
                "R-EVM-RED-001",
                "R-MARGIN-LOW-001",
                "R-UTILIZATION-LOW-001"
        ))
        .author("ydsz-pmis-team")
        .downloadCount(0L)
        .rating(0.0)
        .build();
```

### 4.2 导入规则集（计划）

未来 `RuleTemplateProvider` 扩展支持批量导入规则集：

```java
// 计划 API（v2.0 实现）
public interface RulePackProvider {
    /** 列出市场全部规则集 */
    List<RulePack> listAllPacks();

    /** 按行业查询规则集 */
    List<RulePack> listByIndustry(String industry);

    /** 按编码查询规则集 */
    RulePack findByPackCode(String packCode);

    /** 一键导入规则集为正式规则 */
    List<RuleDefinition> importPack(String packCode, String operator);
}
```

导入流程：

```text
                 ┌────────────────────────────┐
                 │  RulePackProvider          │
                 │  .importPack(code, op)     │
                 └──────────┬─────────────────┘
                            │
                  1. findByPackCode(packCode)
                            │
              ┌─────────────┴──────────────┐
              │ 规则集存在？               │
              └─────────────┬──────────────┘
                            │ 是
                            ▼
              遍历 ruleCodes
                            │
                            ▼
              对每条 ruleCode 调用
              RuleTemplateProvider.importTemplate(code, op)
                            │
                            ▼
              收集保存后的 RuleDefinition 列表
                            │
                            ▼
                  返回导入的规则定义列表
```

### 4.3 市场检索示例（计划）

```java
@Autowired
private RulePackProvider packProvider;

public List<RulePack> listFinancePacks() {
    // 按行业筛选，按下载量降序
    return packProvider.listByIndustry("FINANCE").stream()
            .sorted(Comparator.comparingLong(RulePack::getDownloadCount).reversed())
            .toList();
}
```

## 5. 与规则模板的关系

| 维度 | `RuleTemplateMeta`（P2-5） | `RulePack`（P2-7） |
|------|----------------------------|---------------------|
| 粒度 | 单条规则模板 | 多条规则聚合 |
| 用途 | 一键导入单条规则 | 一键导入整包规则 |
| 引用 | 持有完整表达式模板 | 仅引用 `RuleDefinition.code` |
| 版本 | 无独立版本 | 有 `packVersion` |
| 市场属性 | `usageCount` | `downloadCount` + `rating` |
| 关系 | 规则集可包含多个模板实例化后的规则 | 规则集是模板的聚合产物 |

典型工作流：
1. 运营团队在 `pmis_rule_template` 表中维护单条规则模板（P2-5）
2. 将多条相关模板实例化为规则，聚合为 `RulePack` 发布到市场（P2-7）
3. 业务方按行业检索规则集，一键导入整包，自动创建对应 `RuleDefinition`

## 6. 限制与后续演进

### 6.1 当前限制

1. **仅 DTO**：1.4.0 仅定义 `RulePack` 元数据 DTO，未提供 `RulePackProvider` SPI 与持久层实现
2. **无持久层**：未定义 `pmis_rule_pack` 表与 `RulePackDO`，规则集暂存内存
3. **无市场服务**：未实现规则集发布、下载、评分等市场服务
4. **`ruleCodes` 弱引用**：仅引用 `code`，未校验规则是否存在；规则被删除后规则集可能引用失效
5. **无版本升级**：未定义规则集版本升级与回滚流程

### 6.2 后续演进路径

- **P3.0 规则集市场服务**：实现 `RulePackProvider` SPI 与 `pmis_rule_pack` 持久层，支持 CRUD 与一键导入
- **P3.1 评分与下载统计**：记录用户下载与评分行为，更新 `downloadCount` / `rating`
- **P3.2 版本管理**：`pmis_rule_pack_version` 表记录每个版本的规则集快照，支持升级与回滚
- **P3.3 远程市场**：接入远程规则集市场（HTTP API），支持跨实例共享
- **P3.4 依赖校验**：导入规则集时校验 `ruleCodes` 对应的规则是否存在，缺失时提示用户
