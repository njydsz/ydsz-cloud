# Agent 运行时指标集成指南

## 目标

`AgentRuntimeMetrics` 已作为 Bean 注入到 Spring 容器，下文给出关键埋点位置与调用示例，覆盖：

| 指标 | 说明 | 埋点位置建议 |
|---|---|---|
| `agent_execution_total` | Agent 端到端执行 | `ChatService.chat` / `ChatService.stream` / `DagOrchestrationExecutor.execute` / `AgentExecutor.execute` |
| `agent_tool_calls_total` | 工具调用 | 工具执行器（`ToolExecutor` 实现） |
| `agent_rag_retrieval_total` | RAG 检索 | `HybridRetriever.retrieve` / `RagService.query` |
| `agent_llm_ttft_seconds` | 流式首 Token 耗时 | `ChatService.stream` 首个 chunk 到达时 |
| `agent_active_conversations` | 活跃对话数 | 会话写入（创建/追加消息）时递增；定时任务对账 |
| `agent_conversation_messages_total` | 会话消息累积 | 每次 `memory.save()` 调用后递增 |
| `agent_dag_nodes_executed_total` | DAG 节点 | `DagOrchestrationExecutor.executeNodeLogic` 单节点出口 |
| `agent_human_approval_waiting_total` | Human-in-the-Loop | `HumanApprovalService.submit` 发出审批时 |
| `agent_human_approval_wait_duration_seconds` | 审批等待时长 | 审批结果返回时 |

## 调用示例

### 1. 在 ChatService 注入并埋点会话活跃度

```java
// 构造函数新增参数
private final AgentRuntimeMetrics runtimeMetrics;
...
public ChatService(LlmClient llmClient, ConversationMemory memory, AgentProperties properties,
                   List<InputGuardrail> inputGuardrails, List<OutputGuardrail> outputGuardrails,
                   AgentMetrics metrics, AgentRuntimeMetrics runtimeMetrics,     // ← 新增
                   CostAnalysisService costAnalysisService, TraceRecorder traceRecorder,
                   ObjectProvider<OutboxService> outboxServiceProvider) {
    ...
    this.runtimeMetrics = runtimeMetrics;
}

public ChatResponse chat(...) {
    runtimeMetrics.markConversationActive();
    runtimeMetrics.recordMessage("user");
    ...
    runtimeMetrics.recordMessage("assistant");
    runtimeMetrics.recordExecution("simple", success, durationMs);
```

### 2. 在 DagOrchestrationExecutor 埋点

```java
runtimeMetrics.recordDagExecutionDuration(totalElapsed);
runtimeMetrics.recordDagNode(success ? "success" : "failed");
```

### 3. 在工具执行器

```java
long start = System.currentTimeMillis();
try {
    Object result = tool.execute(args);
    runtimeMetrics.recordToolCall(toolName, null, System.currentTimeMillis() - start);
} catch (Exception e) {
    runtimeMetrics.recordToolCall(toolName, e, System.currentTimeMillis() - start);
}
```

### 4. 定时对账活跃对话数（建议每分钟一次）

```java
@Scheduled(fixedRate = 60_000)
public void reconcile() {
    long count = memory.activeCount(java.time.Duration.ofMinutes(5));
    runtimeMetrics.reconcileActiveConversations(count);
}
```

## 指标值规范

| 字段 | 允许值 |
|---|---|
| `type` | `simple` / `react` / `plan_execute` / `router` / `rag` / `dag` |
| `status` | `success` / `failure` / `timeout` / `skipped` / `empty` |
| `provider` | `pgvector` / `memory` / `hybrid` |
| `tool_name` | 取自 `@Tool.name()` 或 Bean 名称 |
| `role` (message) | `user` / `assistant` / `system` / `tool` |

> 注：指标值统一使用小写 + 下划线命名，与现有 `agent_` 前缀保持一致。

## 告警阈值建议

- `agent_execution_total{status="failure"}` 失败率 > 5% ⇒ 警告；> 20% ⇒ 紧急
- `agent_llm_ttft_seconds` P99 > 3s ⇒ Provider 性能退化
- `agent_active_conversations` > 预设容量 80% ⇒ 容量告警
- `agent_human_approval_wait_duration_seconds` P99 > 30min ⇒ 审批流程积压

## 接入顺序建议

1. **先观测再优化**：部署 1~2 天收集基线后再配告警阈值
2. **优先埋点**：`agent_execution_total` + `agent_tool_calls_total` 最重要的两类
3. **再补全**：DAG、RAG、TTFT；Human Approval 频率较低可后置

---

创建时间：2026-08-04  
维护者：ydsz-team
