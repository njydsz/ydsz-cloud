/**
 * Agent 模块 API 层，定义对外暴露的 DTO 与 RPC 接口.
 *
 * <p>本模块作为 Agent 子系统的 API 契约层，定义了所有跨模块、跨服务调用所用的数据传输对象（DTO）
 * 与 Feign/HTTP 接口签名。上层消费方（如前端 BFF、其他微服务）仅依赖本模块的 API 包即可发起对 Agent 能力
 * 的调用，无需引入业务实现依赖，符合 hexagonal 架构的端口-适配器设计原则。</p>
 *
 * <p>主要 DTO 覆盖以下场景：</p>
 * <ul>
 *   <li>{@code ChatRequestDTO} / {@code ChatResponseDTO} -- 单次对话的请求与响应模型</li>
 *   <li>{@code BatchChatRequestDTO} / {@code BatchChatResponseDTO} -- 批量对话场景下的聚合请求与响应</li>
 *   <li>{@code RagQueryDTO} -- RAG 检索查询请求，携带检索参数与过滤条件</li>
 *   <li>{@code DocumentIngestDTO} -- 文档摄入请求，描述待入库文档的元数据与内容引用</li>
 *   <li>{@code AgentExecutionRequestDTO} / {@code DagExecutionDTO} -- Agent 执行与 DAG 运行请求</li>
 *   <li>{@code PromptTemplateDTO} -- Prompt 模板数据传输对象</li>
 * </ul>
 *
 * <h3>API 设计原则</h3>
 *
 * <ul>
 *   <li>所有 DTO 不可变，使用记录式（record）或 final 字段设计</li>
 *   <li>入参 DTO 与出参 DTO 严格分离，避免读写职责混淆</li>
 *   <li>Feign fallback 接口位于 {@code fallback} 子包，保障远程调用降级路径</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.agent.api;
