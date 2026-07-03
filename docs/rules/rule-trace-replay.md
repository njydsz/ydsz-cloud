<!--
  ===========================================================================
  文件名: rule-trace-replay.md
  路径:   docs/rules/rule-trace-replay.md
  作用:   LiteRule 1.4.0+ 执行链路追踪与轨迹回放（Rule Trace Replay）的设计目标、
          REST API、回放流程、编程式调用与限制说明
  关联:   ydsz-pmis-project/RuleAdminController  /  rule-canary.md  /  rule-conflict-detection.md
  ===========================================================================
-->

# 执行轨迹回放

> 适用于 1.4.0 起。LiteRule 在每次规则评估时通过 `TraceRecorder` 异步落盘执行链路（含事实快照 `factsSnapshot` 与结果快照 `resultSnapshot`），并提供基于 `traceId` 的回放能力：用当前规则集对历史事实重新评估，输出 `added / removed / unchanged` 三类差异，用于规则变更后的影响范围验证、历史告警复盘与回归测试。

## 1. 设计目标

- **链路可追溯**：同一次评估批次共享同一 `traceId`，按 `createdAt` 升序串联成完整执行链路，每条 trace 记录包含 `ruleCode / ruleName / scenario / triggered / severity / conditionResult / elapsedMs` 等字段
- **事实可重放**：每条 trace 落盘时同步保存 `factsSnapshot`（取自 `RuleContext.facts` 的拷贝），保证回放输入与历史评估完全一致，避免"当时数据已变更"导致的不可重现
- **结果可对比**：回放以 `factsSnapshot` 为输入，调用 `RuleAdminService.dryRun(null, facts)` 用当前规则集重新评估，对比历史触发集合与当前触发集合，输出 `added / removed / unchanged` 三类差异
- **零侵入回放**：回放走 dry-run 通道（`ruleCode=null` 评估全部规则），不触发告警下发、不写回 trace 表、不影响熔断器/统计计数，可在生产环境安全执行
- **多维度查询**：支持按 `traceId` 精确查询、按 `ruleCode` 查询最近 N 条、按时间倒序查询最近链路，覆盖"事故定位 / 规则复盘 / 整体巡检"三类运营场景
- **留痕可关联**：trace 与灰度发布（`canaryBucket`）、A/B 测试（`ABTestReport`）共享 `traceId` 命名空间，可在同一上下文下串联灰度路由结果与回放差异

## 2. 数据模型

`pmis_rule_execution_trace` 表（对应 `RuleExecutionTraceDO`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT (AUTO) | 主键 |
| `trace_id` | VARCHAR | 追踪 ID，同一批次评估共享 |
| `rule_code` | VARCHAR(128) | 规则编码 |
| `rule_name` | VARCHAR | 规则名称（冗余，便于直接展示） |
| `scenario` | VARCHAR | 业务场景（取自 `RuleContext.scenario`） |
| `triggered` | BOOLEAN | 是否触发 |
| `severity` | VARCHAR | 触发严重度（RED/YELLOW/BLUE 等） |
| `condition_result` | VARCHAR | 条件表达式求值结果描述（即 `RuleResult.threshold`） |
| `elapsed_ms` | BIGINT | 执行耗时（毫秒） |
| `facts_snapshot` | JSONB | 事实数据快照（`RuleContext.facts` 的拷贝） |
| `result_snapshot` | JSONB | 结果快照（`triggered / severity / title / description`） |
| `error_message` | VARCHAR | 异常信息（评估抛错时记录，便于排查） |
| `created_at` | DATETIME | 创建时间 |

落盘时机：`DefaultRuleEngine.evaluate` 中每条规则评估完成后，异步调用 `traceRecorder.record(trace)`；即便规则评估抛异常也会记录 trace（`error_message` 非空），便于事后排查。

## 3. REST API

所有端点位于 `RuleAdminController`，基础路径 `/api/v1/rules`。

### 3.1 按 traceId 查询执行链路

```
GET /api/v1/rules/traces/{traceId}
```

**请求示例**：

```bash
curl -X GET 'http://localhost:8080/api/v1/rules/traces/trace-20260703-0001' \
  -H 'Accept: application/json'
```

**响应示例**：

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": 1001,
      "traceId": "trace-20260703-0001",
      "ruleCode": "R-EVM-001",
      "ruleName": "EVM 红灯数超限告警",
      "scenario": "EVM_DAILY_CHECK",
      "triggered": true,
      "severity": "RED",
      "conditionResult": "evmRedCount=5, threshold=3",
      "elapsedMs": 12,
      "factsSnapshot": {
        "tenantId": "T001",
        "projectId": "P2026-001",
        "evmRedCount": 5,
        "amount": 12000.00
      },
      "resultSnapshot": {
        "triggered": true,
        "severity": "RED",
        "title": "EVM 红灯数 5 超过阈值 3",
        "description": "项目 P2026-001 EVM 红灯数告警"
      },
      "errorMessage": null,
      "createdAt": "2026-07-03T09:12:35"
    },
    {
      "id": 1002,
      "traceId": "trace-20260703-0001",
      "ruleCode": "R-FIN-001",
      "ruleName": "利润率预警",
      "scenario": "EVM_DAILY_CHECK",
      "triggered": false,
      "severity": null,
      "conditionResult": "grossMargin=0.08, threshold=0.05",
      "elapsedMs": 4,
      "factsSnapshot": { "tenantId": "T001", "projectId": "P2026-001", "evmRedCount": 5, "amount": 12000.00 },
      "resultSnapshot": { "triggered": false, "severity": null, "title": null, "description": null },
      "errorMessage": null,
      "createdAt": "2026-07-03T09:12:35"
    }
  ]
}
```

返回结果按 `createdAt` 升序，构成同一 `traceId` 下从首条规则到末条规则的完整执行链路。

### 3.2 按规则编码查询最近链路

```
GET /api/v1/rules/traces/rule/{ruleCode}?limit=20
```

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `ruleCode` | path | - | 规则编码 |
| `limit` | query | `20` | 返回条数 |

**请求示例**：

```bash
curl -X GET 'http://localhost:8080/api/v1/rules/traces/rule/R-EVM-001?limit=10'
```

**响应示例**：

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": 2048,
      "traceId": "trace-20260703-0099",
      "ruleCode": "R-EVM-001",
      "ruleName": "EVM 红灯数超限告警",
      "scenario": "EVM_DAILY_CHECK",
      "triggered": true,
      "severity": "RED",
      "conditionResult": "evmRedCount=4, threshold=3",
      "elapsedMs": 9,
      "factsSnapshot": { "tenantId": "T001", "projectId": "P2026-009", "evmRedCount": 4 },
      "resultSnapshot": { "triggered": true, "severity": "RED", "title": "...", "description": "..." },
      "errorMessage": null,
      "createdAt": "2026-07-03T15:42:11"
    }
  ]
}
```

结果按 `createdAt` 倒序，便于"最近一次该规则触发情况"的快速定位。

### 3.3 执行回放

```
POST /api/v1/rules/traces/{traceId}/replay
```

**请求示例**：

```bash
curl -X POST 'http://localhost:8080/api/v1/rules/traces/trace-20260703-0001/replay' \
  -H 'Accept: application/json'
```

请求体为空。回放输入完全取自该 `traceId` 下首条 trace 的 `factsSnapshot`，避免外部伪造事实数据。

**响应示例**：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "traceId": "trace-20260703-0001",
    "factsSnapshot": {
      "tenantId": "T001",
      "projectId": "P2026-001",
      "evmRedCount": 5,
      "amount": 12000.00
    },
    "historicalTraces": [
      { "id": 1001, "ruleCode": "R-EVM-001", "triggered": true,  "severity": "RED",    "createdAt": "2026-07-03T09:12:35" },
      { "id": 1002, "ruleCode": "R-FIN-001", "triggered": false, "severity": null,    "createdAt": "2026-07-03T09:12:35" }
    ],
    "currentResults": [
      { "ruleCode": "R-EVM-001",  "triggered": true,  "severity": "RED",    "title": "EVM 红灯数 5 超过阈值 3", "description": "..." },
      { "ruleCode": "R-EVM-NEW",  "triggered": true,  "severity": "YELLOW", "title": "金额异常增长",                "description": "..." }
    ],
    "diff": {
      "added":    ["R-EVM-NEW"],
      "removed":  [],
      "unchanged": ["R-EVM-001"],
      "summary": "新增触发 1 条，移除触发 0 条，保持不变 1 条"
    }
  }
}
```

`diff` 字段语义：

| 字段 | 含义 |
|------|------|
| `added` | 历史未触发、当前触发（规则变更后**新增**的命中） |
| `removed` | 历史触发、当前未触发（规则变更后**消失**的命中） |
| `unchanged` | 历史 / 当前均触发（命中保持不变） |
| `summary` | 中文汇总，便于直接展示在运营看板 |

> 差异集合基于 `ruleCode` 比对，触发判定以 `RuleResult.triggered == true` 为准；只关心"是否触发"，不直接对比 `severity` 变更（如需对比严重度变化，可遍历 `historicalTraces` 与 `currentResults` 自行关联）。

### 3.4 查询最近执行链路

```
GET /api/v1/rules/traces?limit=50
```

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `limit` | query | `50` | 返回条数 |

**请求示例**：

```bash
curl -X GET 'http://localhost:8080/api/v1/rules/traces?limit=20'
```

**响应示例**：

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": 2048,
      "traceId": "trace-20260703-0099",
      "ruleCode": "R-EVM-001",
      "ruleName": "EVM 红灯数超限告警",
      "scenario": "EVM_DAILY_CHECK",
      "triggered": true,
      "severity": "RED",
      "conditionResult": "evmRedCount=4, threshold=3",
      "elapsedMs": 9,
      "factsSnapshot": { "tenantId": "T001", "projectId": "P2026-009", "evmRedCount": 4 },
      "resultSnapshot": { "triggered": true, "severity": "RED", "title": "...", "description": "..." },
      "errorMessage": null,
      "createdAt": "2026-07-03T15:42:11"
    }
  ]
}
```

按 `createdAt` 倒序返回最近的执行记录（跨所有 `traceId`），适用于"最近一小时整体告警量 / 异常规则分布"巡检。

## 4. 执行回放流程

```text
                 ┌──────────────────────────────────┐
                 │ POST /traces/{traceId}/replay     │
                 └──────────────┬───────────────────┘
                                │
                  1. 按 traceId 查询全部历史 trace
                     （按 createdAt 升序）
                                │
                                ▼
                 ┌──────────────────────────────────┐
                 │ trace 列表为空？                   │
                 └──────────────┬───────────────────┘
                       是 ──────┴────── 否
                       │                │
                       ▼                ▼
              返回 fail          取首条 trace.factsSnapshot
              "未找到执行记录"          │
                                       ▼
                 ┌──────────────────────────────────┐
                 │ factsSnapshot 为空？              │
                 └──────────────┬───────────────────┘
                       是 ──────┴────── 否
                       │                │
                       ▼                ▼
              返回 fail        ruleAdminService.dryRun(null, facts)
              "事实快照为空"     （用当前规则集重新评估）
                                       │
                                       ▼
                 ┌──────────────────────────────────┐
                 │ 构建历史触发集合                    │
                 │ historicalTriggered =              │
                 │   traces.where(triggered==true)    │
                 │        .map(ruleCode).toSet()       │
                 └──────────────┬───────────────────┘
                                       │
                                       ▼
                 ┌──────────────────────────────────┐
                 │ 构建当前触发集合                    │
                 │ currentTriggered =                │
                 │   currentResults.map(ruleCode)    │
                 │        .toSet()                   │
                 └──────────────┬───────────────────┘
                                       │
                                       ▼
                 ┌──────────────────────────────────┐
                 │ 集合差异计算                        │
                 │   added    = current - historical  │
                 │   removed  = historical - current  │
                 │   unchanged= current ∩ historical   │
                 └──────────────┬───────────────────┘
                                       │
                                       ▼
                 ┌──────────────────────────────────┐
                 │ 组装响应                           │
                 │  - traceId                         │
                 │  - factsSnapshot                   │
                 │  - historicalTraces                │
                 │  - currentResults                  │
                 │  - diff{added,removed,unchanged,   │
                 │         summary}                   │
                 └──────────────────────────────────┘
```

**关键点**：

- 回放走 `dryRun` 通道，`ruleCode=null` 表示对当前规则集中所有启用的规则重新评估，不限于历史 trace 中出现的规则（这正是"新增触发"差异的来源）
- `factsSnapshot` 取自首条 trace（按 `createdAt` 升序的第一条），同一 `traceId` 下所有 trace 的 `factsSnapshot` 应当一致（同一评估上下文），首条只是约定取值入口
- 历史触发集合只看 `triggered == true` 的记录，未触发的规则不进入差异比对（避免历史未触发但当前仍未触发的规则噪音）

## 5. 编程式调用

### 5.1 通过 Mapper 查询 + 通过 RuleAdminService 回放

```java
@Autowired
private RuleExecutionTraceMapper traceMapper;

@Autowired
private RuleAdminService ruleAdminService;

public ReplayResult replay(String traceId) {
    // 1. 查询历史链路
    List<RuleExecutionTraceDO> traces = traceMapper.selectList(
        new LambdaQueryWrapper<RuleExecutionTraceDO>()
            .eq(RuleExecutionTraceDO::getTraceId, traceId)
            .orderByAsc(RuleExecutionTraceDO::getCreatedAt));

    if (traces.isEmpty()) {
        throw new IllegalStateException("未找到 traceId=" + traceId + " 的执行记录");
    }

    // 2. 取首条事实快照
    Map<String, Object> facts = traces.get(0).getFactsSnapshot();
    if (facts == null || facts.isEmpty()) {
        throw new IllegalStateException("事实快照为空，无法回放");
    }

    // 3. 用当前规则集重新评估
    List<RuleResult> currentResults = ruleAdminService.dryRun(null, facts);

    // 4. 差异计算
    Set<String> historicalTriggered = traces.stream()
        .filter(t -> Boolean.TRUE.equals(t.getTriggered()))
        .map(RuleExecutionTraceDO::getRuleCode)
        .collect(Collectors.toSet());

    Set<String> currentTriggered = currentResults.stream()
        .map(RuleResult::getRuleCode)
        .collect(Collectors.toSet());

    Set<String> added    = new LinkedHashSet<>(currentTriggered);
                    added.removeAll(historicalTriggered);
    Set<String> removed  = new LinkedHashSet<>(historicalTriggered);
                    removed.removeAll(currentTriggered);
    Set<String> unchanged = new LinkedHashSet<>(currentTriggered);
                    unchanged.retainAll(historicalTriggered);

    return new ReplayResult(traceId, facts, traces, currentResults,
        new Diff(added, removed, unchanged));
}
```

### 5.2 典型应用场景

**场景 A：规则变更后影响范围验证**

运营修改 `R-EVM-001` 的条件表达式（阈值从 `>= 3` 调整为 `>= 4`）后，对最近 7 天内该规则触发的所有 `traceId` 逐一回放，统计 `added / removed` 分布，确认变更不会误伤已稳定告警的项目：

```java
// 取最近 7 天该规则的所有触发 traceId
List<String> traceIds = traceMapper.selectList(
    new LambdaQueryWrapper<RuleExecutionTraceDO>()
        .eq(RuleExecutionTraceDO::getRuleCode, "R-EVM-001")
        .eq(RuleExecutionTraceDO::getTriggered, true)
        .ge(RuleExecutionTraceDO::getCreatedAt, LocalDateTime.now().minusDays(7)))
    .stream().map(RuleExecutionTraceDO::getTraceId).distinct().toList();

for (String tid : traceIds) {
    ReplayResult r = replay(tid);
    if (!r.getDiff().getRemoved().isEmpty()) {
        log.warn("[Replay] traceId={} 变更后不再触发: {}", tid, r.getDiff().getRemoved());
    }
}
```

**场景 B：历史告警复盘**

线上事故复盘时，定位到某条告警的 `traceId`，回放后查看"如果用当前规则集评估当时的事实数据，会触发哪些规则"，对比历史实际触发的规则，判断当时漏告警 / 误告警的根因是否已在后续规则迭代中修复。

**场景 C：回归测试**

将一批代表性 `traceId`（覆盖各业务场景）固化为回归用例集，每次规则集发布前批量回放，断言 `removed` 为空（不引入告警丢失），`added` 仅包含预期新增的规则命中，作为发布门禁。

## 6. 限制与后续演进

### 6.1 当前限制

1. **仅取首条事实快照**：同一 `traceId` 下所有 trace 的 `factsSnapshot` 约定一致，回放仅取首条；若历史链路中存在规则中途修改 facts 的场景（当前 `DefaultRuleEngine` 不修改 `RuleContext.facts`，但自定义扩展需注意），回放输入可能与部分规则的"实际评估时刻"事实不一致
2. **差异只看触发与否**：`added / removed / unchanged` 基于 `ruleCode` + `triggered` 比对，不直接输出"同一规则严重度从 RED 变为 YELLOW"这类 severity 变更差异；如需对比，需自行关联 `historicalTraces` 与 `currentResults` 的 `severity` 字段
3. **不回放决策表 / 评分卡**：`dryRun(null, facts)` 当前仅评估 `ExpressionRule`；决策表规则（`DecisionTableRule`）、评分卡规则不参与回放，对应历史 trace 中的 `ruleCode` 不会出现在 `currentResults` 中（会被误算入 `removed`）
4. **不回放规则熔断 / 超时状态**：回放走 dry-run 通道，不经过 `circuitBreaker` / `timeoutExecutor`，无法复现"当时因熔断而未触发"的场景
5. **不写回放 trace**：回放产生的 `currentResults` 不写回 `pmis_rule_execution_trace` 表，无法在事后查询"上一次回放的结果"；如需留痕需调用方自行记录
6. **大事实快照存储成本**：`factsSnapshot` 直接落 JSONB，对字段较多的事实（如包含大数组 / 嵌套对象）会产生存储与 IO 压力，需结合 `traceRecorder.isEnabled()` 与采样策略控制写入量

### 6.2 后续演进路径

- **P2-3 回放报告持久化**：将回放结果（`diff` + `currentResults`）单独落表 `pmis_rule_replay_report`，支持按 `traceId` / `operator` / 时间范围查询历史回放记录，便于审计
- **严重度变更差异**：扩展 `diff` 结构，输出 `severityChanged` 列表（`{ruleCode, from, to}`），完整覆盖"触发不变但严重度调整"的场景
- **决策表 / 评分卡回放**：将 `DecisionTableEvalService` / 评分卡评估能力纳入回放通道，统一 `currentResults` 来源
- **批量回放 API**：提供 `POST /api/v1/rules/traces/batch-replay`，接受 `traceIds` 列表与 `since` 时间范围，返回汇总报告（新增 / 移除 规则 TopN、影响项目数等），适配回归测试门禁
- **采样策略**：在 `TraceRecorder` 层支持按 `traceId` 哈希采样（如 10%），降低生产环境 facts 快照存储成本，同时保证回放可追溯性
