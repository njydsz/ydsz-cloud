<!--
  ===========================================================================
  文件名: rule-template-spi.md
  路径:   docs/rules/rule-template-spi.md
  作用:   LiteRule 1.4.0+ 规则模板服务化 SPI（RuleTemplateProvider）设计目标、接口、使用示例
  关联:   ydsz-pmis-literule 源码  /  rule-conflict-detection.md  /  rule-expression-validation.md
  ===========================================================================
-->

# 规则模板服务化 SPI

> 适用于 1.4.0 起。LiteRule 将规则模板市场的访问能力抽象为 `RuleTemplateProvider` SPI，由 literule 模块定义接口与元数据 DTO，project 模块提供实现，实现引擎模块与持久层的解耦。

## 1. 设计目标

- **依赖反转**：literule 模块不再直接依赖 `RuleTemplateService` / `RuleTemplateDO`，通过 SPI 接口由消费方提供实现，避免循环依赖
- **元数据精简**：`RuleTemplateMeta` 剥离审计字段（`id` / `createdBy` / `createdAt`）与运行时字段（`priority` / `scope` / `titleTemplate` / `descriptionTemplate`），仅保留市场展示所需字段
- **多数据源适配**：实现方可从 MySQL（`pmis_rule_template` 表）、配置中心或远程市场拉取，对 literule 透明
- **测试友好**：测试环境可注入 mock 实现，无需启动数据库
- **市场热度**：新增 `usageCount` 字段反映模板被引用次数，供市场排序
- **标签结构化**：`tags` 由逗号分隔字符串转为 `List<String>`，便于前端渲染与检索

## 2. 核心类

| 类 | 路径 | 职责 |
|----|------|------|
| `RuleTemplateProvider` | `literule.spi` | 规则模板提供者 SPI 接口，定义查询与导入能力 |
| `RuleTemplateMeta` | `literule.spi` | 模板元数据 DTO（只读视图），与持久层 `RuleTemplateDO` 解耦 |
| `RuleTemplateService` | `project.literule` | 现有实现（project 模块），后续适配为 `RuleTemplateProvider` 实现 |
| `RuleTemplateDO` | `project.entity` | 持久层对象，映射 `pmis_rule_template` 表 |

## 3. 接口定义

```java
package com.njydsz.pmis.literule.spi;

import com.njydsz.pmis.literule.api.RuleDefinition;
import java.util.List;

public interface RuleTemplateProvider {
    /** 列出全部模板 */
    List<RuleTemplateMeta> listAll();

    /** 按类别查询 */
    List<RuleTemplateMeta> listByCategory(String category);

    /** 按行业查询 */
    List<RuleTemplateMeta> listByIndustry(String industry);

    /** 按编码查询 */
    RuleTemplateMeta findByCode(String templateCode);

    /** 导入模板为规则定义 */
    RuleDefinition importTemplate(String templateCode, String operator);
}
```

## 4. 数据结构

### 4.1 `RuleTemplateMeta`

```java
@Data
@Builder
public class RuleTemplateMeta implements Serializable {
    private String templateCode;        // 模板编码（唯一）
    private String templateName;        // 模板名称
    private String category;            // 模板类别（FINANCE / EVM / BENCH）
    private String industry;            // 适用行业编码
    private String description;         // 模板描述
    private String conditionTemplate;   // 条件表达式模板（Aviator 语法）
    private String severityTemplate;    // 严重度表达式模板（Aviator 语法，可选）
    private String defaultSeverity;     // 默认严重度编码（RED / YELLOW / INFO / GREEN）
    private List<String> tags;          // 标签列表
    private long usageCount;            // 被引用次数
}
```

### 4.2 与 `RuleTemplateDO` 的字段映射

| `RuleTemplateDO`（持久层） | `RuleTemplateMeta`（SPI） | 转换说明 |
|----------------------------|---------------------------|----------|
| `id` | — | 剥离（内部主键） |
| `templateCode` | `templateCode` | 直接映射 |
| `templateName` | `templateName` | 直接映射 |
| `category` | `category` | 直接映射 |
| `description` | `description` | 直接映射 |
| `conditionExpression` | `conditionTemplate` | 重命名，强调"模板"语义 |
| `severityExpression` | `severityTemplate` | 重命名，强调"模板"语义 |
| `defaultSeverity` | `defaultSeverity` | 直接映射 |
| `industry` | `industry` | 直接映射 |
| `tags`（逗号分隔字符串） | `tags`（`List<String>`） | 按逗号切分为列表 |
| `priority` | — | 剥离（运行时字段，导入时由实现方决定） |
| `scope` | — | 剥离（运行时字段） |
| `titleTemplate` | — | 剥离（运行时字段） |
| `descriptionTemplate` | — | 剥离（运行时字段） |
| `createdBy` | — | 剥离（审计字段） |
| `createdAt` | — | 剥离（审计字段） |
| — | `usageCount` | 新增（市场热度，由实现方从 `pmis_rule_def` 统计） |

## 5. 使用示例

### 5.1 查询模板列表

```java
@Autowired
private RuleTemplateProvider templateProvider;

public List<RuleTemplateMeta> listFinanceTemplates() {
    return templateProvider.listByCategory("FINANCE");
}
```

### 5.2 一键导入模板为规则

```java
@Autowired
private RuleTemplateProvider templateProvider;

public RuleDefinition importTemplate(String templateCode, String operator) {
    return templateProvider.importTemplate(templateCode, operator);
}
```

`importTemplate` 内部流程：

```text
                 ┌────────────────────────────┐
                 │  RuleTemplateProvider      │
                 │  .importTemplate(code, op) │
                 └──────────┬─────────────────┘
                            │
                  1. findByCode(templateCode)
                            │
              ┌─────────────┴──────────────┐
              │ 模板存在？                 │
              └─────────────┬──────────────┘
                            │ 是
                            ▼
              构造 RuleDefinition
              （code/templateCode，name/templateName，...）
                            │
                            ▼
              RuleAdminService.save(definition, operator)
                            │
                            ▼
                  返回保存后的 RuleDefinition
                  （含版本号、审核字段）
```

### 5.3 project 模块适配为 SPI 实现（计划）

现有 `RuleTemplateService` 后续将实现 `RuleTemplateProvider` 接口，并将 `RuleTemplateDO` 转换为 `RuleTemplateMeta`：

```java
@Service
@RequiredArgsConstructor
public class RuleTemplateService implements RuleTemplateProvider {

    private final RuleTemplateMapper ruleTemplateMapper;
    private final RuleAdminService ruleAdminService;

    @Override
    public List<RuleTemplateMeta> listAll() {
        return ruleTemplateMapper.selectList(null).stream()
                .map(this::toMeta)
                .toList();
    }

    private RuleTemplateMeta toMeta(RuleTemplateDO source) {
        return RuleTemplateMeta.builder()
                .templateCode(source.getTemplateCode())
                .templateName(source.getTemplateName())
                .category(source.getCategory())
                .industry(source.getIndustry())
                .description(source.getDescription())
                .conditionTemplate(source.getConditionExpression())
                .severityTemplate(source.getSeverityExpression())
                .defaultSeverity(source.getDefaultSeverity())
                .tags(parseTags(source.getTags()))
                .usageCount(0L)  // TODO: 从 pmis_rule_def 统计引用次数
                .build();
    }
}
```

> 注：以上为计划实现示例，当前阶段未修改 `RuleTemplateService`，仅提供 SPI 接口与 DTO。

## 6. 限制与后续演进

### 6.1 当前限制

1. **仅接口与 DTO**：1.4.0 仅定义 SPI 接口与 `RuleTemplateMeta` DTO，未修改 `RuleTemplateService` 实现，project 模块尚未适配
2. **`usageCount` 暂为占位**：实现方需从 `pmis_rule_def` 表统计 `code` 等于 `templateCode` 的规则数量
3. **不支持模板参数化**：当前模板为静态表达式，不支持 `${param}` 占位符替换（如阈值随行业变化）
4. **无权限控制**：SPI 未定义导入权限校验，由实现方在 `importTemplate` 中补充

### 6.2 后续演进路径

- **P2-7 规则集市场**：将多个 `RuleTemplateMeta` 聚合为 `RulePack`，支持整包导入（见 `rule-pack-market.md`）
- **模板参数化**：支持 `${industry.threshold}` 占位符，导入时按行业配置替换
- **远程市场**：实现方接入远程规则集市场，`RuleTemplateProvider` 从 HTTP 拉取模板
- **模板版本化**：`RuleTemplateMeta` 增加 `version` 字段，支持模板升级与回滚
- **模板冲突检测**：导入前检测模板实例化后的规则与现有规则的冲突（见 `rule-conflict-detection.md` §8.2）
