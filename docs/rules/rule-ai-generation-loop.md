# AI 生成规则闭环

> 适用于 1.4.0 起。AI 生成的规则在保存前必须通过表达式校验 + dryRun 试探，并强制进入 DRAFT 状态，经过人工审批 API 才能进入 PUBLISHED，审批全程留痕（reviewedBy/reviewedAt/reviewComment）。

## 1. 设计目标

- **保存前闭环校验**：AI 生成的规则在保存前必须通过 condition/severity/template 三段表达式校验，任一失败即抛异常阻塞保存
- **dryRun 试探**：用空 facts 调用 `RuleAdminService.dryRun` 试评估一次，确保运行时不会因 NPE/ClassCastException 崩溃
- **强制 DRAFT 状态**：AI 生成的规则 `status=DRAFT`，`enabled=false`，不能直接生效
- **审批留痕**：通过 `/approve` 和 `/reject` API 进入 PUBLISHED 或 ARCHIVED，强制记录审批人、审批时间、审批意见
- **来源可追溯**：`changeDesc` 中追加 `[AI 生成]` 前缀，便于版本历史审计
- **状态机保障**：DRAFT → PUBLISHED 的转换合法性由 P1-2 的 `RuleStatus.canTransitionTo` 强制校验

## 2. 闭环流程

```text
        ┌──────────────────────────────────────┐
        │  RuleGenerationService.generateAndSave │
        └────────────────┬─────────────────────┘
                         │
              ┌──────────┴──────────┐
              │ 1. generate()       │
              │   AI / fallback    │
              └──────────┬──────────┘
                         │
              ┌──────────┴──────────┐
              │ 2. validateGeneratedExpressions │
              │   - condition      │
              │   - severity        │
              │   - titleTemplate   │
              │   - descriptionTemplate │
              └──────────┬──────────┘
                         │ 任一失败
                         ▼
              抛 IllegalArgumentException 阻塞保存
                         │
                         │ 全部通过
                         ▼
              ┌──────────┴──────────┐
              │ 3. performDryRunProbe │
              │   空 facts 试评估     │
              └──────────┬──────────┘
                         │ 异常（仅记录日志，不阻塞）
                         │
                         ▼
              ┌──────────┴──────────┐
              │ 4. 强制 status=DRAFT │
              │    强制 enabled=false │
              └──────────┬──────────┘
                         │
                         ▼
              ┌──────────┴──────────┐
              │ 5. ruleAdminService.save │
              │    changeDesc=[AI 生成] │
              └──────────┬──────────┘
                         │
                         ▼
                  保存为 DRAFT（待审批）
                         │
                         ▼
              ┌──────────────────────────────┐
              │  POST /api/v1/rules/{code}/approve │
              │  或 /reject                      │
              └──────────────────────────────┘
```

## 3. REST API

### 3.1 AI 生成并保存

**POST** `/api/v1/rules/ai-generate-and-save`

请求体：

```json
{
  "description": "当 CPI 低于 0.85 且项目预算超 50万时红色预警",
  "availableFields": ["cpi", "budgetAmount", "evmRedCount"]
}
```

Header: `X-Operator: zhangsan`

响应：

```json
{
  "code": 200,
  "data": {
    "code": "AI_GEN_CPI",
    "name": "AI 生成 - CPI 预警",
    "status": "DRAFT",
    "enabled": false,
    "version": 1,
    "conditionExpression": "cpi < 0.85",
    "severityExpression": "cpi < 0.70 ? 'RED' : 'YELLOW'",
    "defaultSeverity": "YELLOW",
    "titleTemplate": "CPI ${cpi} 偏低",
    "descriptionTemplate": "CPI 为 ${cpi}，低于阈值 0.85"
  }
}
```

### 3.2 审批通过

**POST** `/api/v1/rules/{ruleCode}/approve`

请求体：

```json
{
  "comment": "表达式逻辑正确，符合业务预期"
}
```

Header: `X-Operator: reviewer-li`

行为：
- 校验当前状态 `DRAFT` 或 `REVIEW` 才能审批通过
- 设置 `status=PUBLISHED`，`enabled=true`
- 记录 `reviewedBy=reviewer-li`，`reviewedAt=now()`，`reviewComment=表达式逻辑正确...`
- `changeDesc` 记录 `[审批通过] DRAFT -> PUBLISHED, 审批人=reviewer-li, 意见=...`

响应：

```json
{
  "code": 200,
  "data": {
    "code": "AI_GEN_CPI",
    "status": "PUBLISHED",
    "enabled": true,
    "reviewedBy": "reviewer-li",
    "reviewedAt": "2026-07-03T16:30:00",
    "reviewComment": "表达式逻辑正确，符合业务预期"
  }
}
```

### 3.3 审批驳回

**POST** `/api/v1/rules/{ruleCode}/reject`

请求体：

```json
{
  "reason": "条件表达式过于宽泛，会触发大量误报"
}
```

Header: `X-Operator: reviewer-li`

行为：
- 校验当前状态 `DRAFT/REVIEW/PUBLISHED` 才能驳回
- `reason` 必填，否则返回 400
- 设置 `status=ARCHIVED`，`enabled=false`
- 记录 `reviewedBy`，`reviewedAt`，`reviewComment=[驳回] 条件表达式过于宽泛...`
- `changeDesc` 记录 `[审批驳回] DRAFT -> ARCHIVED, 审批人=reviewer-li, 理由=...`

响应：

```json
{
  "code": 200,
  "data": {
    "code": "AI_GEN_CPI",
    "status": "ARCHIVED",
    "enabled": false,
    "reviewedBy": "reviewer-li",
    "reviewedAt": "2026-07-03T16:31:00",
    "reviewComment": "[驳回] 条件表达式过于宽泛，会触发大量误报"
  }
}
```

## 4. 编程式调用

```java
@Autowired
private RuleGenerationService generationService;
@Autowired
private RuleAdminService ruleAdminService;

// 1. AI 生成并保存（自动进入 DRAFT 状态）
RuleDefinition draft = generationService.generateAndSave(
    "当 CPI 低于 0.85 时黄色预警",
    List.of("cpi", "budgetAmount"),
    "operator-zhang"
);
assert draft.getStatus().equals("DRAFT");
assert !draft.isEnabled();

// 2. 人工审批通过（通过 REST API 或直接调用 RuleAdminService）
RuleDefinition toApprove = ruleAdminService.getByCode(draft.getCode());
toApprove.setStatus(RuleStatus.PUBLISHED.name());
toApprove.setEnabled(true);
toApprove.setReviewedBy("reviewer-li");
toApprove.setReviewedAt(LocalDateTime.now().toString());
toApprove.setReviewComment("审批通过");
ruleAdminService.save(toApprove, "reviewer-li", "[审批通过] DRAFT -> PUBLISHED");
```

## 5. 校验失败处理

### 5.1 条件表达式语法错误

```java
generateAndSave("...", List.of(), "operator")
→ 抛 IllegalArgumentException
   "AI 生成的条件表达式无效 [SYNTAX_ERROR]: could not compile expression: a > (expr=a > )"
```

### 5.2 沙箱拦截

```java
generateAndSave("执行系统命令", List.of(), "operator")
→ AI 生成包含 Runtime.getRuntime().exec(...) 的表达式
→ 抛 IllegalArgumentException
   "AI 生成的条件表达式无效 [SANDBOX_VIOLATION]: 表达式包含危险操作..."
```

### 5.3 模板未闭合

```java
generateAndSave("...", List.of(), "operator")
→ AI 生成的 titleTemplate = "项目 ${projectName 的状态"
→ 抛 IllegalArgumentException
   "AI 生成的标题模板无效 [TEMPLATE_FORMAT_ERROR]: 模板存在未闭合的占位符..."
```

## 6. 状态转换矩阵（P1-2 保障）

| 当前状态 | 可转换到 | 审批 API |
|---------|---------|---------|
| DRAFT | REVIEW / PUBLISHED / ARCHIVED | `/approve` → PUBLISHED；`/reject` → ARCHIVED |
| REVIEW | PUBLISHED / DRAFT | `/approve` → PUBLISHED；`/reject` → ARCHIVED（需走 PUBLISHED→ARCHIVED 间接路径） |
| PUBLISHED | DISABLED / ARCHIVED | `/reject` → ARCHIVED |
| DISABLED | PUBLISHED / ARCHIVED | 不支持审批 |
| ARCHIVED | （终态） | 不支持任何转换 |

## 7. 与其他模块的协作

| 模块 | 协作点 |
|------|--------|
| P1-2 状态机 | `RuleAdminService.validateStatusTransition` 保障 DRAFT → PUBLISHED 合法性 |
| P1-4 冲突检测 | AI 生成规则保存时也会触发冲突检测，避免与现有规则冲突 |
| P1-5 表达式校验 | `ExpressionValidationService` 提供 condition/severity/template 校验 |
| P0-5 灰度发布 | 审批通过后可叠加 `canaryRatio` 进行小流量验证 |

## 8. 限制与后续演进

### 8.1 当前限制

1. **dryRun 试探使用空 facts**：仅验证运行时不崩溃，不校验业务正确性（空 facts 下大多数条件返回 false）
2. **不区分 AI 来源**：当前 `changeDesc` 统一标记 `[AI 生成]`，不区分 LLM 生成 vs fallback 降级生成
3. **审批不支持多级**：当前只支持单级审批，不支持多级会签
4. **不强制审批人权限**：任何调用 `/approve` 的用户都可以审批，权限校验由网关层处理

### 8.2 后续演进路径

- **多级审批**：DRAFT → REVIEW → PUBLISHED 三级流程，REVIEW 状态需要更高权限审批人
- **审批人推荐**：结合 AgentClient 的 `APPROVER_RECOMMEND` 能力，自动推荐合适的审批人
- **审批通知**：审批通过/驳回后通过 RocketMQ 通知规则创建人
- **AI 来源标记字段**：在 `pmis_rule_def` 新增 `source` 字段（AI / MANUAL / IMPORT），便于运营统计 AI 生成规则的采纳率
