# ydsz-agent

> AI Agent 智能体服务 — LLM 对话、Agent 编排、Tool Calling、RAG 知识增强、记忆管理

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9010** |
| **服务名** | `ydsz-agent` |
| **构建顺序** | 13/13 |
| **数据库** | PostgreSQL（`pmis_agent_*` 表） |
| **依赖** | Nacos、PostgreSQL、Redis |

## 核心职责

### 1. P0 能力（当前版本）

| 能力 | 说明 |
|---|---|
| **LLM Provider 抽象** | `LlmClient` 统一接口 + OpenAI 兼容实现（覆盖 GPT/DeepSeek/Qwen/Moonshot/智谱） |
| **同步对话** | `POST /agent/chat` — 完整请求/响应 |
| **流式对话（SSE）** | `POST /agent/chat/stream` — 逐 token 推送 |
| **对话管理** | `Conversation` 聚合 + `ChatMessage` 值对象 + Redis 滑动窗口记忆 |
| **Prompt 模板** | `PromptTemplate` + `#{var}` 变量替换 |
| **多模型路由** | `LlmClientRouter` + Fallback 降级 |
| **Token 计量** | 每次 LLM 调用记录 prompt/completion/total tokens |
| **健康检查** | `/actuator/health` 暴露 LLM Provider 和 Memory 状态 |

### 2. 后续规划（P1-P4）

| 阶段 | 能力 |
|---|---|
| **P1** | ReAct Agent + Tool Calling + PMIS 工具集 + Memory 策略 + 安全护栏 |
| **P2** | RAG 知识增强 + pgvector + 文档摄入 Pipeline + nextwiki 集成 |
| **P3** | Plan-Execute + Router Agent + 多 Agent 协作 + DSL 编排 |
| **P4** | 调试器 + Prompt 平台 + 成本分析 + Marketplace |

## 使用方式

### 1. 配置

```yaml
pmis:
  agent:
    enabled: true
    llm:
      default-provider: openai          # openai / deepseek / qwen / ollama
      default-model: gpt-4o-mini
      api-key: ${LLM_API_KEY}
      base-url: https://api.openai.com/v1
      temperature: 0.7
      max-tokens: 2048
      timeout-seconds: 60
    memory:
      ttl-hours: 24
      max-messages: 20
```

### 2. 同步对话

```bash
curl -X POST http://localhost:9010/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请介绍一下PMIS系统"}'
```

### 3. 流式对话（SSE）

```bash
curl -N -X POST http://localhost:9010/agent/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "帮我分析项目进度"}'
```

### 4. 对话历史

```bash
# 获取历史
curl http://localhost:9010/agent/history?conversationId=xxx

# 清除历史
curl -X DELETE http://localhost:9010/agent/history?conversationId=xxx
```

## 目录结构

```
ydsz-agent/
├── ydsz-agent-api/          # Feign 客户端 + DTO
├── ydsz-agent-domain/       # 领域模型（Agent/Conversation/Tool/Memory/Prompt）
├── ydsz-agent-infra/        # LLM Provider + Redis 记忆 + 向量存储
├── ydsz-agent-server/       # 应用服务（ChatService + Config + Health）
└── ydsz-agent-web/          # REST API + 启动入口
```

## 技术选型

| 决策 | 方案 | 理由 |
|---|---|---|
| LLM API | OpenAI 兼容 API | 事实标准，覆盖 90% 国产模型 |
| 流式输出 | SseEmitter + WebClient | 非 WebSocket，轻量级 |
| 记忆存储 | Redis List | 滑动窗口 + TTL 自动过期 |
| 向量存储 | PostgreSQL pgvector（P2） | 复用现有 PG 基础设施 |
| 工具调用 | 自研 @Tool 注解（P1） | 轻量级，无额外依赖 |

---

> 本模块是 PMIS AI 能力的核心入口，与 literule（规则引擎）、workflow（工作流）、message（消息引擎）三引擎深度融合。
