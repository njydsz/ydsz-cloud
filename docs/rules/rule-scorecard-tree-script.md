<!--
  ===========================================================================
  文件名: rule-scorecard-tree-script.md
  路径:   docs/rules/rule-scorecard-tree-script.md
  作用:   LiteRule 1.4.0 评分卡/决策树/脚本三类高级规则的持久化设计
  版本:   1.4.0
  迁移:   V1.0.0_048__init_rule_scorecard_tree_script.sql
  关联:   ydsz-pmis-literule 源码  /  rule-canary.md
  ===========================================================================
-->

# 评分卡 / 决策树 / 脚本规则持久化

> 对应版本：LiteRule 1.4.0  
> 数据库迁移：`V1.0.0_048__init_rule_scorecard_tree_script.sql`  
> 关联 SPI：`ScorecardConfigProvider` / `DecisionTreeConfigProvider` / `ScriptConfigProvider`

## 1. 设计目标

在表达式规则、决策表规则之外，LiteRule 1.4.0 起补齐三类高级规则的可持久化能力：

| 规则类型 | 适用场景 | 求值引擎 | 持久化表 |
|---------|---------|---------|---------|
| 评分卡（Scorecard） | 多因子加权打分、客户/项目评级 | Aviator | `pmis_rule_scorecard` |
| 决策树（Decision Tree） | 多层条件路由、风险分级 | Aviator | `pmis_rule_decision_tree` |
| 脚本（Script） | Aviator 无法覆盖的复杂逻辑 | Groovy JSR-223 | `pmis_rule_script` |

三类规则统一遵循 **Definition DTO + SPI Provider + `from` 工厂方法 + RuleHotReloader 动态加载** 的架构，与表达式规则保持一致的运营体验（热刷新、启停、版本号）。

## 2. 数据库表结构

### 2.1 pmis_rule_scorecard（评分卡）

| 字段 | 类型 | 说明 |
|------|------|------|
| rule_code | VARCHAR(128) | 规则编码（唯一） |
| rule_name | VARCHAR(256) | 规则名称 |
| category | VARCHAR(64) | 类别，默认 RISK |
| base_score | NUMERIC(10,2) | 基础分，默认 100 |
| red_threshold | NUMERIC(10,2) | 红色阈值（总分低于此值 → RED） |
| yellow_threshold | NUMERIC(10,2) | 黄色阈值（总分低于此值 → YELLOW） |
| factors | JSONB | 评分因子数组 |
| priority | INTEGER | 优先级，默认 100 |
| enabled | BOOLEAN | 是否启用 |
| scope | VARCHAR(128) | 影响范围 |
| version | INTEGER | 版本号 |

`factors` JSON 结构：

```json
[
  {"conditionExpression": "overdueCount > 3", "score": -30, "description": "逾期次数过多"},
  {"conditionExpression": "paymentRatio < 0.5", "score": -20, "description": "付款比率过低"}
]
```

### 2.2 pmis_rule_decision_tree（决策树）

| 字段 | 类型 | 说明 |
|------|------|------|
| rule_code | VARCHAR(128) | 规则编码（唯一） |
| rule_name | VARCHAR(256) | 规则名称 |
| category | VARCHAR(64) | 类别 |
| root_node | JSONB | 决策树根节点（嵌套结构） |
| priority | INTEGER | 优先级 |
| enabled | BOOLEAN | 是否启用 |
| scope | VARCHAR(128) | 影响范围 |
| version | INTEGER | 版本号 |

`root_node` JSON 结构（递归）：

```json
{
  "conditionExpression": "budgetUsedRatio > 0.9",
  "leaf": false,
  "trueBranch":  {"leaf": true, "severity": "RED",    "title": "严重超支", "description": "预算使用率超过 90%"},
  "falseBranch": {
    "conditionExpression": "budgetUsedRatio > 0.7",
    "leaf": false,
    "trueBranch":  {"leaf": true, "severity": "YELLOW", "title": "中度超支", "description": "预算使用率超过 70%"},
    "falseBranch": {"leaf": true, "severity": "INFO",   "title": "正常",     "description": "预算使用正常"}
  }
}
```

### 2.3 pmis_rule_script（脚本）

| 字段 | 类型 | 说明 |
|------|------|------|
| rule_code | VARCHAR(128) | 规则编码（唯一） |
| rule_name | VARCHAR(256) | 规则名称 |
| category | VARCHAR(64) | 类别 |
| script | TEXT | Groovy 脚本内容 |
| default_severity | VARCHAR(16) | 默认严重度，默认 INFO |
| sandbox_enabled | BOOLEAN | 是否启用沙箱，默认 TRUE |
| priority | INTEGER | 优先级 |
| enabled | BOOLEAN | 是否启用 |
| scope | VARCHAR(128) | 影响范围 |
| version | INTEGER | 版本号 |

## 3. SPI 接口

消费方（如 `ydsz-pmis-execution` 模块）实现以下 SPI，从数据库加载定义。literule 模块本身不依赖持久层。

```java
public interface ScorecardConfigProvider {
    List<ScorecardDefinition> loadEnabledScorecards();
    List<ScorecardDefinition> loadAllScorecards();
    ScorecardDefinition save(ScorecardDefinition definition, String operator);
    void toggleEnabled(String ruleCode, boolean enabled, String operator);
    ScorecardDefinition findByCode(String ruleCode);
    void delete(String ruleCode, String operator);
}
```

`DecisionTreeConfigProvider`、`ScriptConfigProvider` 接口结构与之相同，仅泛型不同。

## 4. from 工厂方法

三类 Rule 实现类提供静态工厂方法，将 Definition 转换为可执行 Rule：

```java
// 评分卡
ScorecardRule rule = ScorecardRule.from(definition, evaluator);

// 决策树
DecisionTreeRule rule = DecisionTreeRule.from(definition, evaluator);

// 脚本
ScriptRule rule = ScriptRule.from(definition);
```

工厂方法内部完成：
- 评分卡：将 `ScorecardDefinition.ScoreFactor` 转换为内部 `ScorecardRule.ScoreFactor`
- 决策树：递归 `convertNode` 转换嵌套节点，容错解析 severity 字符串
- 脚本：编译 Groovy 脚本（沙箱模式下编译前执行 `checkScriptSafety` 正则拦截）

## 5. 热加载流程

`RuleHotReloader` 在启动时与收到 `RuleConfigRefreshEvent` 时按以下顺序加载五类规则：

```
fullReload(operator)
  ├─ loadExpressionRules()    （必需 SPI：RuleConfigProvider）
  ├─ loadDecisionTables()    （可选 SPI：DecisionTableConfigProvider）
  ├─ loadScorecards()        （可选 SPI：ScorecardConfigProvider）
  ├─ loadDecisionTrees()     （可选 SPI：DecisionTreeConfigProvider）
  └─ loadScripts()           （可选 SPI：ScriptConfigProvider）
```

当某类 SPI Bean 未注入时，对应规则类型被跳过（向后兼容）。单规则刷新时按相同顺序尝试匹配 `ruleCode`，命中即返回。

## 6. 评分卡语义

- **总分计算**：`totalScore = baseScore + Σ(命中因子 score)`，最终钳制到 `[0, 100]`
- **严重度映射**（注意分数越低风险越高）：
  - `totalScore < redThreshold` → RED
  - `totalScore < yellowThreshold` → YELLOW
  - 其他 → INFO
- **因子求值**：每个因子的 `conditionExpression` 通过 Aviator 求值，单因子异常不影响其他因子

## 7. 脚本规则沙箱

沙箱模式（默认启用）通过正则拦截危险 API：

- `System.exit` / `Runtime.getRuntime` / `ProcessBuilder`
- `Class.forName` / `ClassLoader` / `loadClass`
- `FileInputStream` / `FileOutputStream` / `RandomAccessFile`
- `Socket` / `URL.openConnection` / `HttpURLConnection`
- `exec` / `invokeMethod` / `ScriptEngine` / `GroovyShell` / `Eval.` / `Thread.sleep`

脚本约定：
- 通过 `facts` 变量（`Map<String, Object>`）访问事实数据
- 返回 `boolean`：`true` = 触发，`false` = 不触发
- 可设置 `severity` / `title` / `description` 变量自定义结果

## 8. 配置

`LiteRuleAutoConfiguration` 自动装配：

- `ruleHotReloader` Bean 通过 `ObjectProvider` 注入四个可选 SPI（决策表/评分卡/决策树/脚本），缺失时对应类型不加载。
- `lite-rule.properties`：`literule.hot-reload-enabled=true` 控制热加载开关。

## 9. 单元测试

`RuleFromDefinitionTest` 覆盖四个场景：

1. 评分卡：baseScore=100，命中 -30/-20 两个因子 → 总分 50 → RED
2. 决策树：三档 budgetUsedRatio（0.95/0.75/0.5）分别命中 RED/YELLOW/INFO
3. 脚本：复合条件触发，动态设置 severity/title/description
4. 沙箱：含 `Runtime.getRuntime().exec` 的脚本在 `from` 阶段抛出 `SecurityException`
