/**
 * Agent 基础设施层，提供 LLM 客户端、向量存储、对话记忆、工具注册、Trace 存储等具体实现.
 *
 * <p>本模块作为 Agent 子系统的基础设施适配层，实现领域层定义的各类网关接口。涵盖大模型调用、
 * 向量数据库存储、对话记忆持久化、工具自动扫描注册、执行链路追踪、护栏检查、文档解析与 Text2SQL
 * 等适配器实现，同时提供 MyBatis Mapper 与 Spring Data 风格的 Repository 实现类。</p>
 *
 * <p>关键实现要点：</p>
 * <ul>
 *   <li>{@code CompatibleLlmClient} 对接大模型服务，支持多模型路由（{@code LlmClientRouter}）与语义缓存（{@code SemanticLlmCache}）</li>
 *   <li>{@code PgVectorStore} 基于 PostgreSQL pgvector 扩展实现 {@code VectorStore} 接口，支持混合检索（{@code HybridRetriever}）</li>
 *   <li>{@code RedisConversationMemory} / {@code SummaryConversationMemory} 提供滑动窗口与摘要压缩两种对话记忆策略</li>
 *   <li>{@code DefaultToolRegistry} 通过 {@code ToolAnnotationScanner} 扫描注解自动注册工具</li>
 *   <li>{@code PgTraceRecorder} / {@code InMemoryTraceRecorder} 实现执行链路追踪的持久化与内存版本</li>
 *   <li>护栏适配器 {@code PiiMaskingGuardrail} 和 {@code PromptInjectionGuardrail} 分别处理 PII 脱敏与注入检测</li>
 * </ul>
 *
 * <h3>主要适配器</h3>
 *
 * <ul>
 *   <li>{@code CompatibleLlmClient} -- LLM 客户端</li>
 *   <li>{@code PgVectorStore} -- 基于 pgvector 的向量存储实现</li>
 *   <li>{@code CompatibleEmbeddingClient} -- 嵌入模型客户端</li>
 *   <li>{@code RedisConversationMemory} -- Redis 对话记忆实现</li>
 *   <li>{@code DefaultToolRegistry} -- 默认工具注册中心</li>
 *   <li>{@code PgTraceRecorder} -- PostgreSQL 执行链路追踪记录器</li>
 *   <li>{@code SimpleTextChunker} / {@code IdentityReranker} -- 文本分片与重排序实现</li>
 *   <li>{@code ToolAdapter} -- 工具适配器，桥接 MCP 协议工具</li>
 *   <li>{@code Text2SQLTool} / {@code JdbcText2SQLService} -- Text2SQL 适配器与查询服务</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.agent.infra;
