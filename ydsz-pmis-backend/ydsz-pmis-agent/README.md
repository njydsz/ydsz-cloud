# ydsz-pmis-agent

> AI 智能体编排服务（Multi-Agent Orchestration）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9007**（按构建顺序 8/8） |
| **服务名** | `ydsz-pmis-agent` |
| **构建顺序** | 8/8 |
| **数据库** | PostgreSQL |
| **依赖** | Nacos、PostgreSQL、Redis、可选 LLM Provider（OpenAI / 灵积 / 千帆） |

## 核心职责

本模块是 PMIS 的 **AI 智能体引擎**，对标 AgentScope / LangGraph 多智能体编排框架。

### 1. 5 大内置 Agent

| Agent | 职责 |
|---|---|
| **PredictionAgent** | 业务预测（成本 / 利润 / 工期） |
| **DocumentAgent** | 文档生成（合同 / 报告 / 邮件） |
| **QA Agent** | 智能问答（基于知识库） |
| **CockpitAgent** | 驾驶舱数据解读 |
| **AuditAgent** | 风险审计 / 合规检查 |

### 2. 4 种编排策略

| 策略 | 说明 |
|---|---|
| **Serial** 串行 | 任务按顺序执行，前置 Agent 输出作为后置输入 |
| **Parallel** 并行 | 多个 Agent 同时执行，结果聚合 |
| **Voting** 投票 | 多个 Agent 输出投票决策 |
| **Cascade** 级联 | 主 Agent 失败时降级到备用 Agent |

### 3. 5 LLM Provider 路由

| Provider | 协议 | 默认模型 |
|---|---|---|
| **mock** | 内置 | - |
| **spring-ai-openai** | OpenAI 兼容 | gpt-4o-mini |
| **dashscope** | 阿里云灵积 | qwen-turbo |
| **qianfan** | 百度千帆 | ERNIE-Speed |
| **OpenAI-Compatible** | 自定义 | 自定义 |

切换 Provider：`pmis.agent.llm.provider`

### 4. 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/agent/orchestration` | 编排任务（创建 / 执行 / 查询） |
| `/agent/prediction` | 业务预测 |
| `/agent/document` | 文档生成 |
| `/agent/qa` | 智能问答 |
| `/agent/trace` | 调用链追踪（含 `provider_trace_id`） |
| `/agent/quota` | Token 配额管理 |

## 数据库表设计

本模块在 `deploy/sql/V1.0.0.sql` 中持有 **12 张表**，覆盖智能体编排 + RAG + MCP + 配额 + 链路追踪 + HITL 人工介入。

| 业务域 | 表名 | 说明 |
|---|---|---|
| **DAG 编排** | `pmis_agent_dag_definition` | DAG 定义（多智能体编排图） |
| | `pmis_agent_dag_instance` | DAG 实例 |
| | `pmis_agent_dag_node_instance` | DAG 节点实例 |
| **RAG 知识库** | `pmis_agent_knowledge_base` | 知识库元信息 |
| | `pmis_agent_document` | 文档元信息（MinIO path / 分块数） |
| | `pmis_agent_document_chunk` | 文档切片（含向量化结果） |
| **Prompt** | `pmis_agent_prompt_template` | Prompt 模板（含变量校验） |
| **业务预测** | `pmis_agent_prediction` | 预测结果（成本/利润/工期） |
| **HITL 人工介入** | `pmis_agent_hitl_approval` | 人工审批（ReAct/多步推理中的卡点） |
| **Token 配额** | `pmis_agent_token_quota` | 租户级配额（按 LLM Provider） |
| | `pmis_agent_token_usage_log` | Token 用量流水（按日聚合） |
| **链路追踪** | `pmis_agent_trace` | 调用链追踪（含 `provider_trace_id`） |

> **索引关键点**：
> - `pmis_agent_trace(trace_id)` 唯一
> - `pmis_agent_dag_instance(dag_id, status)` 监控
> - `pmis_agent_dag_node_instance(instance_id, status)` 节点依赖
> - `pmis_agent_document_chunk(knowledge_base_id, document_id)` 向量检索
> - `pmis_agent_token_usage_log(tenant_id, provider, usage_date)` 配额结算
> - `pmis_agent_hitl_approval(approval_id, status, due_date)` 待审批 SLA
>
> **依赖说明**：RAG 文档向量检索依赖 `literule` + `pgvector` 扩展，详见根 README 数据库依赖章节。

## 启动顺序

依赖 `common` + `nacos` + LLM Provider，**应在所有业务服务之后**启动。

## 目录结构

```
ydsz-pmis-agent/
├── pom.xml
└── src/main/
    ├── java/com/njydsz/pmis/agent/
    │   ├── AgentApplication.java
    │   ├── controller/
    │   ├── service/
    │   │   ├── OrchestrationService.java   # 编排核心
    │   │   ├── LlmRouter.java              # LLM Provider 路由
    │   │   ├── BlackboardService.java      # 共享上下文
    │   │   └── agents/                     # 5 Agent 实现
    │   ├── llm/                            # LLM 客户端
    │   │   ├── LlmClient.java
    │   │   ├── MockLlmClient.java
    │   │   ├── OpenAiClient.java
    │   │   ├── DashScopeClient.java
    │   │   └── QianfanClient.java
    │   ├── mapper/ / entity/ / enums/
    │   └── config/
    ├── resources/
    │   ├── bootstrap.yml
    │   ├── mapper/                         # AgentTraceMapper / TokenQuotaMapper
    │   └── config/                         # 原 nacos-config（已重命名）
    │       ├── ydsz-pmis-agent-dev.yaml
    │       ├── ydsz-pmis-agent-sit.yaml
    │       └── ydsz-pmis-agent-uat.yaml
    └── test/
```

## 配置文件

| 变量 | 默认值 | 说明 |
|---|---|---|
| `LLM_PROVIDER` | `mock` | `mock` / `spring-ai-openai` / `dashscope` / `qianfan` |
| `LLM_TIMEOUT_MILLIS` | `10000` | LLM 调用超时 |
| `LLM_MAX_RETRIES` | `2` | 失败重试次数 |
| `LLM_FALLBACK_TO_MOCK` | `true` | 失败降级到 mock |
| `OPENAI_API_KEY` | （必填） | OpenAI 兼容 API Key |
| `OPENAI_BASE_URL` | `https://api.openai.com` | API 地址 |
| `OPENAI_MODEL` | `gpt-4o-mini` | 模型名 |

## 启动

```bash
# 1. 开发环境：使用 mock provider（无需真实 API Key）
cd ydsz-pmis-backend
mvn -pl ydsz-pmis-agent spring-boot:run

# 2. 生产环境：设置真实 LLM 配置
export LLM_PROVIDER=openai
export OPENAI_API_KEY=sk-xxx
mvn -pl ydsz-pmis-agent spring-boot:run
```

## 测试

```bash
mvn -pl ydsz-pmis-agent -am test
```

测试覆盖：
- `OrchestrationServiceTest` 4 种编排策略
- `LlmRouterTest` 5 Provider 路由
- `BlackboardServiceTest` 共享上下文
- `TokenQuotaServiceTest` 配额管理
- `AgentTraceRecorderTest` 链路追踪

## Feign 接口

### 主动调用

- `InitiationFeignClient` → ydsz-pmis-project（拉取项目数据）
- `ExecutionClient` → ydsz-pmis-project（拉取执行数据）

### 被调用

- `AgentClient`（位于 common）→ 各业务服务调用 AI 能力

## 常见问题

### Q1：LLM 调用超时

- 检查 `OPENAI_BASE_URL` 是否可访问
- 调整 `LLM_TIMEOUT_MILLIS`（生产建议 30s）
- 启用 `LLM_FALLBACK_TO_MOCK=true` 保证可用性

### Q2：编排任务执行一半失败

- 检查每个 Agent 的 `@Retryable` / `@CircuitBreaker` 注解
- 4 种编排策略的失败处理逻辑不同
- 查看 `/agent/trace` 定位失败环节

### Q3：Token 配额超限

`TokenQuotaMapper` 记录每个租户的 Token 使用量。可通过 `/agent/quota` 接口查询。

---

> **AI 调用必须异步化**：所有 `/agent/*` 接口都应通过 `MessageClient` 异步回调，
> 避免长时间 HTTP 连接占用 Web 容器线程。
