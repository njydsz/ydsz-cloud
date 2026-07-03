# 规则冲突检测

> 适用于 1.4.0 起。LiteRule 在规则保存前自动检测新规则与现有规则的潜在冲突，输出冲突列表并按级别决定是否阻塞保存，避免出现"同条件不同严重度"等语义冲突以及重复定义、命名混乱等问题。

## 1. 设计目标

- **保存前拦截**：在 `RuleAdminService.save()` 调用 `configProvider.save()` 之前完成冲突检测
- **三类冲突**：覆盖最常见的三种规则配置错误
  - `IDENTICAL_CONDITION`：条件表达式与现有规则完全相同（WARN，可能重复定义）
  - `CONTRADICTORY_SEVERITY`：条件相同但严重度不同（ERROR，语义冲突）
  - `NAME_COLLISION`：同 `category` 下 `name` 相同但条件不同（WARN，命名冲突）
- **可配置阻塞**：ERROR 级别冲突默认阻塞保存，WARN 级别仅记录日志；可通过 `pmis.literule.conflict-detection-block-on-error=false` 关闭阻塞
- **字段精确匹配**：基于字段归一化（去空白 + 转小写）后的精确比较，不做表达式语义分析或样本探测，避免误报
- **租户隔离**：仅在同一 `tenantId` 内检测冲突，跨租户规则不参与对比
- **更新自跳过**：检测时跳过 `ruleCode` 与新规则相同的记录，避免"更新自身"被误判为冲突
- **降级容错**：`configProvider.loadAllRules()` 异常时返回空列表跳过检测，不阻塞正常保存

## 2. 启用方式

`application.yml`：

```yaml
pmis:
  literule:
    enabled: true
    conflict-detection-enabled: true          # 默认 true，关闭后完全不装配检测器
    conflict-detection-block-on-error: true  # 默认 true，ERROR 级别冲突是否阻塞保存
```

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `conflict-detection-enabled` | `true` | 是否启用冲突检测；为 `false` 时 `RuleAdminService` 不装配 `RuleConflictDetector` |
| `conflict-detection-block-on-error` | `true` | ERROR 级别冲突是否阻塞保存；为 `false` 时仅记录日志 |

`conflict-detection-enabled=false` 时，`LiteRuleAutoConfiguration` 不会创建 `RuleConflictDetector` Bean，同时显式调用 `service.setConflictDetectionEnabled(false)`，即便上层手动注入了检测器也不会生效。

## 3. 检测流程

```text
                 ┌────────────────────────────┐
                 │  RuleAdminService.save()    │
                 └──────────┬─────────────────┘
                            │
                  1. evaluator.validate(conditionExpr)
                  2. validateStatusTransition(def)
                            │
                            ▼
              detectConflicts(definition)
                            │
              ┌─────────────┴──────────────┐
              │ conflictDetectionEnabled? │
              │ conflictDetector != null? │
              └─────────────┬──────────────┘
                            │ 是
                            ▼
              conflictDetector.detect(def)
                            │
              ┌─────────────┴──────────────┐
              │ 遍历 loadAllRules() 结果   │
              │ - 跳过自身（ruleCode 相同）│
              │ - 跳过不同租户             │
              │ - normalize 后比较条件    │
              │ - 比较 severityKey         │
              │ - 比较 name + category     │
              └─────────────┬──────────────┘
                            │
              ┌─────────────┴──────────────┐
              │ 遍历冲突列表                │
              │ - ERROR → log.error        │
              │ - WARN  → log.warn         │
              └─────────────┬──────────────┘
                            │
              ┌─────────────┴──────────────┐
              │ hasError && blockOnError?  │
              └─────────────┬──────────────┘
                            │ 是
                            ▼
                  抛 IllegalStateException
                  阻塞 configProvider.save()
```

## 4. 冲突类型与级别

| Type | Level | 触发条件 | 默认行为 |
|------|-------|---------|---------|
| `IDENTICAL_CONDITION` | WARN | 同 `tenantId` 下，`normalize(conditionExpression)` 完全相同，且 `severityKey` 也相同 | 记录日志，不阻塞 |
| `CONTRADICTORY_SEVERITY` | ERROR | 同 `tenantId` 下，`normalize(conditionExpression)` 完全相同，但 `severityKey` 不同 | 阻塞保存（可配置） |
| `NAME_COLLISION` | WARN | 同 `tenantId` + 同 `category` 下，`name` 相同但 `normalize(conditionExpression)` 不同 | 记录日志，不阻塞 |

### 4.1 `normalize` 规则

```java
expression.replaceAll("\\s+", "").toLowerCase()
```

- 去除所有空白字符：`"a > 1"` 与 `"a>1"` 视为相同
- 转小写：`"EVMRedCount >= 3"` 与 `"evmredcount >= 3"` 视为相同
- **不做语法等价判断**：`"a > 1 || b > 2"` 与 `"b > 2 || a > 1"` 视为不同条件

### 4.2 `severityKey` 规则

```java
if (severityExpression != null && !severityExpression.isBlank()) {
    return "expr:" + severityExpression.trim();
}
return "default:" + (defaultSeverity != null ? defaultSeverity.getCode() : "YELLOW");
```

- 优先使用 `severityExpression`，前缀 `expr:` 标识
- 否则使用 `defaultSeverity`，前缀 `default:` 标识
- **`expr:` 与 `default:` 视为不同**：即使最终运行时取值相同，也视为语义冲突（一个静态、一个动态，理应人工确认）

## 5. 冲突示例

### 5.1 `CONTRADICTORY_SEVERITY`（ERROR，默认阻塞）

```text
已有规则 R-EVM-001:
  conditionExpression: evmRedCount >= 3
  defaultSeverity:     RED

新增规则 R-EVM-001-V2:
  conditionExpression: evmRedCount >= 3
  defaultSeverity:     YELLOW
  → 检测到 CONTRADICTORY_SEVERITY 冲突
  → 抛 IllegalStateException 阻塞保存
```

### 5.2 `IDENTICAL_CONDITION`（WARN，不阻塞）

```text
已有规则 R-EVM-001:
  conditionExpression: evmRedCount >= 3
  defaultSeverity:     RED

新增规则 R-EVM-DUP:
  conditionExpression: evmRedCount >= 3
  defaultSeverity:     RED
  → 检测到 IDENTICAL_CONDITION 提示
  → 仅记录日志，保存继续
```

### 5.3 `NAME_COLLISION`（WARN，不阻塞）

```text
已有规则 R-FIN-001:
  name: 利润率预警
  category: FINANCE
  conditionExpression: grossMargin < 0.05

新增规则 R-FIN-002:
  name: 利润率预警
  category: FINANCE
  conditionExpression: grossMargin < 0.03
  → 检测到 NAME_COLLISION 提示
  → 仅记录日志，保存继续
```

### 5.4 跨租户不检测

```text
已有规则（租户 1）R-T1-001:
  conditionExpression: evmRedCount >= 3
  defaultSeverity:     RED
  tenantId: 1

新增规则（租户 2）R-T2-001:
  conditionExpression: evmRedCount >= 3
  defaultSeverity:     YELLOW
  tenantId: 2
  → 跨租户，不检测
  → 保存继续
```

## 6. 编程式调用

冲突检测器是一个独立的服务，可以在保存入口之外的场景单独使用：

```java
@Autowired
private RuleConflictDetector conflictDetector;

public void preCheck(RuleDefinition newDef) {
    List<RuleConflict> conflicts = conflictDetector.detect(newDef);
    long errorCount = conflicts.stream()
            .filter(c -> c.getLevel() == RuleConflict.Level.ERROR)
            .count();
    if (errorCount > 0) {
        // 自定义处理，例如返回给前端
    }
}
```

`RuleConflict` 结构：

```java
public class RuleConflict {
    public enum Level { WARN, ERROR }
    public enum Type {
        IDENTICAL_CONDITION,
        CONTRADICTORY_SEVERITY,
        NAME_COLLISION
    }
    private Type type;
    private Level level;
    private String newRuleCode;
    private String conflictingRuleCode;
    private String description;
}
```

## 7. 单元测试

- 单元测试类：[RuleConflictDetectorTest](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-literule/src/test/java/com/njydsz/pmis/literule/config/RuleConflictDetectorTest.java)
- 覆盖场景：
  1. 条件相同 + 严重度相同 → `IDENTICAL_CONDITION` (WARN)
  2. 条件相同 + 严重度不同 → `CONTRADICTORY_SEVERITY` (ERROR)
  3. `severityExpression` vs `defaultSeverity` 也应识别为不同严重度
  4. 同 `category` + `name` 相同 + 条件不同 → `NAME_COLLISION` (WARN)
  5. 不同 `category` + `name` 相同 → 不触发 `NAME_COLLISION`
  6. 更新场景：自身跳过
  7. 跨租户不检测
  8. 无冲突返回空列表
  9. 条件表达式空白归一化（`"a > 1"` 等价于 `"a>1"`）
  10. 空 `conditionExpression` 不应误判为相同
  11. `configProvider` 抛异常时返回空列表（降级容错）

## 8. 限制与后续演进

### 8.1 当前限制

1. **字段精确匹配**：基于 `normalize` 后的字段精确比较，不做表达式语法树等价分析（如 `"a > 1 || b > 2"` 与 `"b > 2 || a > 1"` 视为不同）
2. **不做条件范围重叠检测**：例如 `"amount > 1000"` 与 `"amount > 500"` 范围包含，但当前版本不会检测到冲突
3. **不做样本探测**：不通过实际数据样本反推冲突，避免误报
4. **仅检测 `ExpressionRule`**：决策表 / 评分卡 / 决策树 / 脚本规则的冲突检测在各自 `AdminService` 中独立实现

### 8.2 后续演进路径

- **P2-4 变量空间元数据**：通过 `VariableRegistry` 提供变量取值范围信息后，可以做条件范围重叠检测
- **P2-5 规则模板服务化**：模板级别的冲突检测，避免同模板实例化时参数重叠
- **样本回放探测**：从 `pmis_rule_trace` 中采样真实上下文，对新旧规则同时评估对比命中差异
