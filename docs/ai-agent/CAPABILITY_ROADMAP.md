# AI Agent 能力扩展规划

> P2-优化：ydz-agent 模块中长期能力规划与扩展路线

## 目录

- [现状评估](#现状评估)
- [行业趋势与竞品分析](#行业趋势与竞品分析)
- [能力分层模型](#能力分层模型)
- [分阶段扩展路线](#分阶段扩展路线)
- [成本与 ROI 分析](#成本与-roi-分析)
- [实施建议](#实施建议)

---

## 现状评估

### 当前 ydz-agent 能力

基于代码库审计，ydsz-agent 已具备：

| 能力 | 状态 | 说明 |
|------|------|------|
| LLM 对话 | ✅ 已完成 | 对接 OpenAI/Ollama 等 |
| RAG 知识库 | ✅ 已完成 | pgvector 向量检索 |
| 工具调用 (Tool Call) | ✅ 已完成 | MCP 协议支持 |
| 多轮对话 | ✅ 已完成 | 会话记忆管理 |
| 流式输出 | ✅ 已完成 | SSE 推送 |
| Agent 工作流 | 🔄 进行中 | 编排复杂任务 |
| 多 Agent 协作 | 📋 规划中 | A2A 协议 |

### 能力差距分析

| 维度 | 现状 | 行业标杆 | 差距 |
|------|------|----------|------|
| 模型支持 | OpenAI/ollama | 多模型智能路由 | 中等 |
| 工具生态 | 自建工具集 | MCP 生态 + 市场 | 较大 |
| 协作能力 | 单 Agent | 多 Agent 协同 | 较大 |
| 规划能力 | 固定链式 | ReAct/Plan & Execute | 中等 |
| 记忆管理 | 短期会话 | 长期记忆 + 知识图谱 | 较大 |
| 安全合规 | 基础过滤 | Agent 沙箱 + 权限分级 | 中等 |

---

## 行业趋势与竞品分析

### 互联网大厂 AI Agent 对标

| 厂商 | 产品 | 核心能力 | 可借鉴点 |
|------|------|----------|----------|
| 微软 | AutoGen / Copilot Studio | 多 Agent 可视化编排 | 低代码 Agent 开发平台 |
| 字节 | Coze / 扣子 | 插件市场 + 工作流 | 丰富的工具生态 + 渠道集成 |
| 阿里 | 通义千问 Agent | 企业级 RAG + MCP | 企业级知识库 + API 治理 |
| 百度 | 文心 Agent | 长期记忆 + 个性化 | 用户画像驱动的个性化回复 |
| Anthropic | Claude + Tool Use | 模型级 Tool Use | 安全可控的工具调用协议 |
| OpenAI | GPTs + Assistants | 生态 + 市场 | Agent 商店模式 |

### 关键趋势

1. **MCP 成为工具连接标准**：Anthropic 发布的 Model Context Protocol 正成为业界通用工具协议
2. **多 Agent 协作成主流**：复杂任务分解到多个专业 Agent 协同完成
3. **长期记忆成刚需**：从会话级记忆扩展到用户全生命周期知识管理
4. **低代码 Agent 开发**：让业务人员（不仅是开发者）可以编排 Agent 工作流
5. **安全与合规沙箱**：Agent 执行的安全边界与权限控制成为企业准入条件

---

## 能力分层模型

```
┌────────────────────────────────────────────────────────────────────────┐
│                    Layer 5: 应用层 (Application)                       │
│     智能助手    │    智能审批    │    智能报表    │    知识问答       │
├────────────────────────────────────────────────────────────────────────┤
│                    Layer 4: 编排层 (Orchestration)                     │
│     工作流引擎   │   多 Agent 协作  │   任务规划    │   结果评估       │
├────────────────────────────────────────────────────────────────────────┤
│                    Layer 3: 能力层 (Capabilities)                      │
│     RAG 检索    │    Tool Use     │   SQL 生成    │   数据分析       │
├────────────────────────────────────────────────────────────────────────┤
│                    Layer 2: 模型层 (Foundation Models)                 │
│     对话模型    │    嵌入模型     │   重排序模型  │   分类模型        │
├────────────────────────────────────────────────────────────────────────┤
│                    Layer 1: 基础设施层 (Infrastructure)                │
│     向量数据库   │   模型网关     │   缓存层      │   可观测性        │
└────────────────────────────────────────────────────────────────────────┘
```

### 各层能力成熟度评估

| 层级 | 当前成熟度 | 目标成熟度 | 优先级 |
|------|-----------|-----------|--------|
| L1 基础设施 | ●●●○○ 60% | ●●●●● 100% | P1 |
| L2 模型层 | ●●●○○ 60% | ●●●●○ 80% | P1 |
| L3 能力层 | ●●○○○ 40% | ●●●●● 100% | P0 |
| L4 编排层 | ●○○○○ 20% | ●●●●○ 80% | P0 |
| L5 应用层 | ●○○○○ 20% | ●●●●○ 80% | P2 |

---

## 分阶段扩展路线

### 第一阶段：能力补齐（Q3，2 个月）

**目标**：补齐核心能力，达到生产可用水平

#### 1.1 模型网关升级

```yaml
capabilities:
  multi-model-routing:     # 多模型智能路由
    - 按成本自动选择模型
    - 按任务类型路由（对话/分析/嵌入）
    - 模型降级熔断
  model-failover:          # 模型故障转移
    - 主备模型切换
    - 超时自动重试
    - 配额耗尽降级
```

```java
// 多模型路由接口设计
public interface ModelRouter {
    
    /**
     * 根据任务智能选择模型
     */
    ModelEndpoint route(ModelRequest request);
    
    /**
     * 获取当前模型健康状态
     */
    ModelHealth health(String modelId);
}
```

#### 1.2 MCP 工具生态扩展

| 工具类别 | 工具列表 | 用途 |
|----------|----------|------|
| HR 相关 | submit_leave, query_leave_balance | 请假申请、余额查询 |
| 审批相关 | approve_task, reject_task, query_task | 审批操作 |
| 项目相关 | create_project, query_project, create_task | 项目管理 |
| 报表相关 | generate_report, export_excel | 数据分析 |
| 日程相关 | create_meeting, query_calendar, send_reminder | 日程管理 |

#### 1.3 长期记忆系统

```
短期记忆（会话级）  ──>  中期记忆（用户级）  ──>  长期记忆（企业级）
     │                       │                       │
 Redis (TTL 2h)         PostgreSQL +              Neo4j +
 当前对话上下文           pgvector 用户画像          知识图谱
```

#### 交付物

- [ ] 模型网关组件 `ydsz-agent-model-router`
- [ ] MCP 工具市场（含 10+ 内置工具）
- [ ] 长期记忆服务（用户画像 + 企业知识图谱）
- [ ] Agent 可观测性面板（Trace + Token 消耗）

---

### 第二阶段：协作增强（Q4，2 个月）

**目标**：支持多 Agent 协同，提升复杂任务解决能力

#### 2.1 多 Agent 协作架构

```
                    ┌─────────────────────────────────────┐
                    │         Orchestrator Agent          │
                    │      (任务分解 / 调度 / 结果汇总)     │
                    └──────┬────────┬────────┬────────────┘
                           │        │        │
              ┌────────────┘        │        └────────────┐
              ▼                     ▼                     ▼
    ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
    │  Explorer Agent  │  │  Reasoner Agent  │  │  Executor Agent  │
    │   (信息收集)     │  │   (推理分析)     │  │   (工具执行)     │
    └─────────────────┘  └─────────────────┘  └─────────────────┘
              │                     │                     │
              └─────────────────────┴─────────────────────┘
                                    │
                          ┌─────────────────┐
                          │    Tool Hub     │
                          │  (MCP Server)   │
                          └─────────────────┘
```

#### 2.2 Agent 角色定义

```java
public enum AgentRole {
    /**
     * 探索者：负责信息收集、搜索、检索
     */
    EXPLORER(
        "你是一个专业的信息收集专家，擅长从海量数据中提取关键信息",
        List.of("web_search", "rag_query", "database_query")
    ),
    
    /**
     * 推理者：负责分析、决策、方案设计
     */
    REASONER(
        "你是一个逻辑推理专家，擅长分析复杂问题并给出解决方案",
        List.of("chain_of_thought", "decision_tree", "risk_analysis")
    ),
    
    /**
     * 执行者：负责调用工具、发送消息、操作数据
     */
    EXECUTOR(
        "你是一个高效的执行者，擅长调用工具完成任务",
        List.of("tool_call", "api_invoke", "workflow_trigger")
    ),
    
    /**
     * 审查者：负责质量检查、安全审核、结果验证
     */
    REVIEWER(
        "你是一个严格的审核专家，确保输出符合规范和安全要求",
        List.of("policy_check", "output_validation", "audit_trail")
    );
}
```

#### 2.3 任务编排引擎

```yaml
# 工作流定义示例（YAML 编排）
workflow: "employee_onboarding"
description: "新员工入职流程"

steps:
  - name: prepare_equipment
    agent: executor
    tool: create_equipment_request
    input:
      employee_id: "${context.employeeId}"
      department: "${context.department}"
      
  - name: setup_accounts
    agent: executor
    tool: create_system_accounts
    parallel: true  # 并行执行
    tasks:
      - create_email
      - create_vpn
      - create_gitlab_account
      
  - name: prepare_training
    agent: reasoner
    require_approval: true
    generate: "基于 ${context.role} 生成培训计划"
    
  - name: summary_report
    agent: reviewer
    input:
      previous_steps: "${steps[*].output}"
    output: "入职准备完成报告"
```

#### 交付物

- [ ] 多 Agent 编排引擎
- [ ] Agent 角色定义 SDK
- [ ] 可视化工流设计器（前端）
- [ ] Agent 执行监控面板
- [ ] A2A (Agent-to-Agent) 通信协议实现

---

### 第三阶段：平台化（Q1 2027，3 个月）

**目标**：对外提供 Agent 开发平台，支持业务团队自建 Agent

#### 3.1 Agent 低代码平台

```
┌────────────────────────────────────────────────────────────────────┐
│                      Agent Studio                                  │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  ┌──────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────────┐  │
│  │ 知识库    │  │  工具配置     │  │ 模型设置 │  │  测试 / 发布 │  │
│  │          │  │              │  │          │  │              │  │
│  │ + 文档   │  │ ■ MCP 工具    │  │ ■ GPT-4  │  │ ● 预览对话   │  │
│  │ + 网页   │  │ ■ HTTP API   │  │ ■ Claude │  │ ● 调试 Trace │  │
│  │ + FAQ   │  │ ■ SQL 查询   │  │ ■ 通义   │  │ ● A/B 测试   │  │
│  │ + 表格   │  │ ■ 内部 API   │  │ ■ 本地   │  │ ● 版本管理   │  │
│  └──────────┘  └──────────────┘  └──────────┘  └──────────────┘  │
│                                                                    │
│  ──────────────────── Agent 编排画布 ────────────────────────────   │
│                                                                    │
│  ┌────────┐     ┌────────┐     ┌────────┐     ┌────────┐          │
│  │ 开始   │────▶│ 知识   │────▶│ 推理   │────▶│ 执行   │          │
│  └────────┘     │ 检索   │     │ 分析   │     │ 操作   │          │
│                  └────────┘     └────────┘     └────────┘          │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

#### 3.2 Agent 共享市场

| 功能 | 说明 |
|------|------|
| 模板市场 | 预置 HR、财务、项目管理等模板 |
| Agent Store | 业务团队发布可复用的 Agent |
| Tool Hub | MCP 工具共享与发现 |
| 贡献积分 | Agent/Tool 被采纳后给予贡献者积分激励 |

#### 3.3 安全与合规

```java
@Configuration
public class AgentSecurityConfig {
    
    /**
     * Agent 执行沙箱
     */
    @Bean
    public AgentSandbox sandbox() {
        return AgentSandbox.builder()
            // 权限边界
            .allowedTools(Set.of("safe_read_tools"))
            // 速率限制
            .rateLimit(RateLimit.perMinute(10))
            // Token 预算
            .tokenBudget(TokenBudget.perSession(100_000))
            // 敏感信息过滤
            .piiFilter(PiiFilter.strict())
            // 审计日志
            .auditLog(AuditLog.enabled())
            .build();
    }
}
```

#### 交付物

- [ ] Agent Studio 低代码平台（前端）
- [ ] Agent 模板市场（含 20+ 模板）
- [ ] Tool Hub（MCP 工具仓库）
- [ ] Agent SDK for Java/Python（供业务团队二次开发）
- [ ] Agent 沙箱安全机制

---

## 成本与 ROI 分析

### 成本结构

| 成本项 | 月度估算 | 说明 |
|--------|----------|------|
| LLM API | ¥5,000 - ¥50,000 | 取决于调用量与模型选择 |
| 向量数据库 | ¥2,000 - ¥5,000 | pgvector 自建 vs 云服务 |
| 计算资源 | ¥3,000 - ¥10,000 | Agent 执行 + 推理 GPU |
| 存储 | ¥500 - ¥2,000 | 会话、知识库、日志 |
| 人力成本 | 2-3 人 | Agent 工程师 |
| **总计** | **¥15,000 - ¥80,000/月** | |

### ROI 估算

| 预期收益 | 月度估算 | 回报周期 |
|----------|----------|----------|
| 客服人力节省 | ¥20,000（替代 2 名客服） | 3-6 月 |
| 审批效率提升 | ¥10,000（节省人工审批时间） | 6-12 月 |
| 知识查询效率 | ¥15,000（员工自助查询节省 IT 人力） | 3-9 月 |
| 数据报表生成 | ¥5,000（替代手动报表） | 即时 |
| 内部协同效率 | ¥10,000（减少跨系统切换） | 6-12 月 |
| **总计** | **¥60,000/月** | **3-9 月回本** |

### 经济效益对比

```
┌─────────────────────────────────────────────────────────────┐
│            成本 vs 收益（月度，单位：万元）                    │
│                                                             │
│  成本 ████████████████ 8万                                   │
│                                                             │
│  收益 ██████████████████████████████ 12万                    │
│                                                             │
│  净收益 ████████████ 4万                                     │
└─────────────────────────────────────────────────────────────┘
```

---

## 实施建议

### 推荐路径

1. **短期（Q3 2027）**：补齐模型网关 + MCP 生态，实现可用
2. **中期（Q4 2027）**：多 Agent 协作 + 长期记忆，实现好用
3. **长期（Q1 2027+）**：低代码平台 + Agent 市场，实现规模化

### 关键决策点

| 决策 | 选项 | 建议 |
|------|------|------|
| 模型选择 | 全量 API vs 自建 + API 混合 | 敏感数据用内部模型，普通任务用 API |
| 工具扩展 | 自研 vs 接入 MCP 生态 | 优先接入 MCP，核心能力自研 |
| 存储架构 | pgvector vs Milvus | 中小规模 pgvector 足够，大规模用 Milvus |
| 监控方案 | 自建 vs 商业 | 自建 LangSmith 风格的监控面板 |

### 风险与应对

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| LLM API 成本高 | 高 | 中 | 模型分级路由 + 缓存常见回复 |
| 输出质量不稳定 | 高 | 高 | 强化 RAG + 输出校验 + 人工反馈循环 |
| 安全合规风险 | 中 | 高 | 沙箱执行 + 敏感信息过滤 + 审计日志 |
| 知识库冷启动 | 中 | 中 | 预置基准知识 + 业务方共同建设 |
| 团队 AI 能力不足 | 中 | 中 | 培训 + 引入 AI 平台工程师 |

---

## 附录：资源与参考

### 开源项目参考

| 项目 | 用途 | 链接 |
|------|------|------|
| LangChain | Agent 编排框架 | github.com/langchain-ai/langchain |
| AutoGen | 多 Agent 协作 | github.com/microsoft/autogen |
| CrewAI | 角色化 Agent | github.com/crewAIInc/crewAI |
| Dify | LLM 应用开发平台 | github.com/langgenius/dify |
| LangSmith | LLM 可观测性 | smith.langchain.com |
| MCP | 模型上下文协议 | github.com/modelcontextprotocol |

### 内部资源

- ydz-agent 模块：`ydsz-backend/ydsz-agent/`
- 向量检索实现：`ydsz-agent/ydsz-agent-rag/`
- 模型接入：`ydsz-agent/ydsz-agent-llm/`
- 工具调用：`ydsz-agent/ydsz-agent-tool/`

---

> 文档更新: 2026-08-04 | 维护人: ydsz-team
