/**
 * Agent 领域层，包含 Agent 执行器接口、对话记忆、向量存储、文本分片、重排序、护栏等领域模型与网关接口.
 *
 * <p>本模块定义了 Agent 子系统的核心领域模型与所有对外网关（Gateway / Repository / Client）接口契约，
 * 不依赖任何具体基础设施实现，保持 pure domain 的纯净性。领域对象涵盖 Agent 定义、Agent DAG、
 * 执行上下文、执行计划、对话会话与消息、Token 用量记录、Prompt 模板等值对象与聚合根。</p>
 *
 * <p>网关接口层通过接口抽象隔离上层业务逻辑与底层实现细节：</p>
 * <ul>
 *   <li>{@code LlmClient} / {@code EmbeddingClient} -- 大模型与嵌入模型调用接口，由基础设施层提供 OpenAI 兼容实现</li>
 *   <li>{@code VectorStore} / {@code Retriever} / {@code Reranker} / {@code TextChunker} -- RAG 检索管线各阶段接口</li>
 *   <li>{@code ConversationMemory} -- 对话记忆接口，支持基于 Redis 的滑动窗口与摘要压缩等多种实现</li>
 *   <li>{@code ToolRegistry} / {@code ToolExecutor} -- 工具注册与执行接口，支撑 Function Calling 能力</li>
 *   <li>{@code TraceRecorder} -- 执行链路追踪记录器接口</li>
 * </ul>
 *
 * <h3>领域模型</h3>
 *
 * <ul>
 *   <li>{@code AgentDefinition} / {@code AgentDag} / {@code AgentExecutionContext} -- Agent 核心聚合</li>
 *   <li>{@code ChatRequest} / {@code ChatResponse} / {@code ChatChunk} / {@code ChatMessage} -- 对话模型</li>
 *   <li>{@code Tool} / {@code ToolCall} / {@code ToolDefinition} -- 工具调用模型</li>
 *   <li>{@code TextChunk} / {@code VectorStore} / {@code Retriever} -- 检索增强模型</li>
 *   <li>{@code InputGuardrail} / {@code OutputGuardrail} / {@code GuardrailResult} -- 护栏模型</li>
 *   <li>{@code TokenUsage} / {@code CostEstimate} / {@code TenantQuota} -- 用量与成本模型</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.agent.domain;
