/**
 * Agent Web 层，提供 REST 控制器与 Spring Boot 自动配置.
 *
 * <p>本模块是 Agent 子系统的用户接口层，以 Spring Boot 应用为核心载体，对外暴露 HTTP/REST API。
 * 通过 Spring MVC 控制器封装 Agent 对话、RAG 检索、Prompt 管理、DAG 编排、调试与可观测性等端点，
 * 并通过 {@code AgentAutoConfiguration} 提供开箱即用的自动装配能力，便于上游模块以 starter 方式集成。</p>
 *
 * <p>控制器按业务域划分为以下分组：</p>
 * <ul>
 *   <li>{@code AgentController} / {@code AgentDefinitionController} / {@code AgentMetadataController} --
 *       Agent 对话与定义管理端点</li>
 *   <li>{@code RagController} -- RAG 检索与文档管理端点</li>
 *   <li>{@code PromptController} -- Prompt 模板管理与评估端点</li>
 *   <li>{@code DagController} -- DAG 编排与执行控制端点</li>
 *   <li>{@code DebugController} -- 调试会话与断点管理端点</li>
 *   <li>{@code ObservabilityController} -- 可观测性与统计面板端点</li>
 *   <li>{@code HumanApprovalController} -- 人工审批回调端点</li>
 * </ul>
 *
 * <h3>自动配置</h3>
 *
 * <ul>
 *   <li>{@code AgentAutoConfiguration} -- 核心自动配置类，装配 Agent 服务、RAG、Prompt、DAG、护栏等 Bean</li>
 *   <li>通过 {@code AgentApplication} 启动 Spring Boot 应用上下文</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.agent.web;
