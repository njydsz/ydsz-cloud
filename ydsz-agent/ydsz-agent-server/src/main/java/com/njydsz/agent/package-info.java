/**
 * Agent 核心业务服务层，负责 Agent 定义管理、对话执行、SSE 流式输出、RAG 检索增强、Prompt 管理、DAG 编排等核心能力.
 *
 * <p>本模块是 Agent 子系统的核心服务实现层，封装了从 Agent 定义 CRUD、Agent 工厂创建、不同执行器
 * （ReAct / Plan-Execute / RAG / Supervisor / Simple）的调度，到对话流式响应与心跳保活的完整链路。
 * 同时提供检索增强生成（RAG）的文档摄入与查询、Prompt 模板的版本化管理、DAG 条件分支与人工审批、
 * 租户配额控制、成本分析、调试与可观测性等高阶能力。</p>
 *
 * <p>关键设计特点：</p>
 * <ul>
 *   <li>通过 {@code AgentFactory} 统一创建各类 Agent 实例，配合 {@code AbstractAgentExecutor} 模板方法提供可复用的执行骨架</li>
 *   <li>{@code ChatService} / {@code SseExecutor} / {@code SseHeartbeatScheduler} 协作完成 SSE 长连接流式输出与心跳续期</li>
 *   <li>{@code RagService} 与 {@code DocumentIngestionService} 构建检索增强管线，
 *       对接领域层的 {@code VectorStore}、{@code Retriever}、{@code TextChunker} 等网关接口</li>
 *   <li>{@code AgentFacadeImpl} 作为对外的聚合门面，统一暴露给 Web 层与其他上游调用方</li>
 *   <li>内置护栏（{@code GuardrailService}、{@code AgentRequestGuard}）对输入输出进行 PII 脱敏与 Prompt 注入防护</li>
 * </ul>
 *
 * <h3>核心组件</h3>
 *
 * <ul>
 *   <li>{@code AgentFacadeImpl} -- 对外的聚合门面，整合 Agent 定义、对话、Prompt、RAG、DAG 等能力</li>
 *   <li>{@code ChatService} -- 对话编排服务，协调 LLM 调用、工具执行与护栏校验</li>
 *   <li>{@code AgentDefinitionServiceImpl} -- Agent 定义 CRUD 与版本快照管理</li>
 *   <li>{@code SseHeartbeatScheduler} -- SSE 心跳调度器，维护长连接活性并推送心跳事件</li>
 *   <li>{@code RagService} -- RAG 查询编排服务，串联检索、重排与上下文注入</li>
 *   <li>{@code DocumentIngestionService} -- 文档摄入服务，负责解析、分片、嵌入与入库</li>
 *   <li>{@code AgentFactory} -- Agent 工厂，根据定义类型实例化对应执行器</li>
 *   <li>{@code AbstractAgentExecutor} -- 抽象执行器基类，定义执行模板与钩子方法</li>
 *   <li>{@code ReActAgentExecutor} / {@code PlanExecuteAgentExecutor} / {@code RagAgentExecutor}
 *       / {@code SupervisorAgentExecutor} / {@code SimpleAgentExecutor} -- 多种执行策略实现</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.agent;
