# 规则灰度发布

> 适用于 1.4.0 起。LiteRule 支持规则级别的灰度发布，将一部分流量路由到候选版本（条件/严重度表达式），用于线上验证新版本表达式是否符合预期。

## 1. 设计目标

- **流量分桶**：按 `canaryRatio` 比例将流量分配到候选桶，主桶评估主版本表达式，候选桶评估候选表达式
- **条件过滤**：`canaryConditions` 提供 Aviator 表达式列表（AND 关系），仅满足条件的流量才进入分桶阶段
- **稳定分桶**：基于 `traceId` 哈希，同一上下文在不同规则上的分桶结果稳定，避免抖动
- **结果标记**：候选桶评估结果自动打上 `canary=true` + `canaryBucket=CANARY`，便于运营对比新旧命中差异
- **统计可见**：`RuleCanaryRouter#getCanaryBucketStats` 暴露 ruleCode → {主桶计数, 候选桶计数}

## 2. 启用方式

`application.yml`：

```yaml
pmis:
  literule:
    enabled: true
    canary-enabled: true   # 默认 true，关闭后引擎不会路由到候选版本
```

`canary-enabled=false` 时，即便规则定义中 `canaryRatio > 0`，也不会触发灰度路由。

## 3. 数据库字段

`pmis_rule_def` 表新增以下字段（见 `V1.0.0_047__add_rule_canary.sql`）：

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `canary_ratio` | NUMERIC(5,4) | 0.0 | 灰度比例，0~1.0；0 表示不灰度 |
| `canary_conditions` | JSONB | NULL | Aviator 表达式数组（AND 关系） |
| `canary_condition_expression` | TEXT | NULL | 候选版本条件表达式（覆盖 `condition_expression`） |
| `canary_severity_expression` | TEXT | NULL | 候选版本严重度表达式（覆盖 `severity_expression`） |

灰度分桶统计表 `pmis_rule_canary_bucket`（按日聚合）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `rule_code` | VARCHAR(128) | 规则编码 |
| `bucket_type` | VARCHAR(16) | PRIMARY / CANARY |
| `bucket_count` | BIGINT | 当日累计命中次数 |
| `stat_date` | DATE | 统计日期 |

## 4. 规则定义示例

```sql
-- 假设原规则 R-EVM-001：amount >= 1000 触发 RED
UPDATE pmis_rule_def
SET canary_ratio = 0.1,
    canary_conditions = '["tenantId == ''T001''"]'::jsonb,
    canary_condition_expression = 'amount >= 800',
    canary_severity_expression = '''YELLOW'''
WHERE rule_code = 'R-EVM-001';
```

含义：
- 10% 流量进入候选桶
- 仅当 `tenantId == 'T001'` 时才进入分桶阶段
- 候选桶使用 `amount >= 800` 触发，严重度为 YELLOW
- 主桶（90%）仍走原 `amount >= 1000 / RED` 逻辑

## 5. 评估流程

```text
                 ┌────────────────────────────┐
                 │  DefaultRuleEngine.evaluate │
                 └──────────┬─────────────────┘
                            │
                  resolveCanaryDefinition
                            │
              ┌─────────────┴──────────────┐
              │ canaryRatio > 0 ?          │
              │ canaryRouter != null ?     │
              │ canaryCondition/Severity   │
              │ Expression 任一非空 ?      │
              └─────────────┬──────────────┘
                            │ 是
                            ▼
              shouldRouteToCanary(def, ctx)
                │
                │ 1. canaryConditions 全部为 true？
                │ 2. 基于 traceId 哈希分桶
                │
              ┌─┴─────────────┐
              │               │
       进入灰度桶             不进入灰度桶
              │               │
              ▼               ▼
    buildCanaryRule           评估主版本
    evaluate (timeout包裹)
              │
              ▼
   markCanary(result) → canary=true, canaryBucket=CANARY
              │
              ▼
   recordBucket(ruleCode, true/false)
```

候选版本评估同样会被 `timeoutExecutor` 包裹、`traceRecorder` 记录、`circuitBreaker` 计数，与主版本评估流程完全一致。

## 6. 运营监控

### 6.1 查询分桶统计

```java
@Autowired
private RuleEngine ruleEngine;

DefaultRuleEngine engine = (DefaultRuleEngine) ruleEngine;
Map<String, long[]> stats = engine.getCanaryRouter().getCanaryBucketStats();
// stats: { "R-EVM-001": [primaryCount, canaryCount], ... }
```

### 6.2 重置分桶统计

灰度开始新一轮测试前可重置：

```java
engine.getCanaryRouter().resetStats();
```

### 6.3 Prometheus 指标（计划）

后续将接入以下指标：
- `literule_canary_bucket_total{rule_code, bucket_type}` Counter
- `literule_canary_triggered_total{rule_code}` Counter

## 7. A/B 测试 vs 灰度发布

| 维度 | ABTestService | RuleCanaryRouter |
|------|---------------|------------------|
| 触发方式 | 手动调用 API | 线上自动路由 |
| 评估版本 | 同时双跑主+候选 | 二选一路由 |
| 适用场景 | 上线前验证 | 上线后小流量验证 |
| 结果输出 | ABTestReport（diff 详情） | 标记 canary=true 的 RuleResult |

建议工作流：先用 `ABTestService` 离线对比，确认无差异后启用 `RuleCanaryRouter` 小流量灰度，灰度 7 天无告警后正式发布。

## 8. 限制与注意事项

1. **仅对 ExpressionRule 生效**：决策表规则（`DecisionTableRule`）和编码规则不参与灰度路由，因为它们的 RuleDefinition 不可直接覆盖
2. **候选版本表达式必须显式提供**：仅设 `canaryRatio > 0` 但不提供 `canaryConditionExpression` / `canarySeverityExpression` 时，路由器视为无候选版本，按主版本评估
3. **traceId 必须传入**：若 `RuleContext.traceId == null`，分桶将退化为随机，导致同一上下文分桶不稳定
4. **熔断器会同时统计主桶和候选桶**：候选版本异常会触发 `circuitBreaker.recordResult`，进而触发规则熔断（这是预期行为，防止候选版本异常拖垮线上稳定性）
