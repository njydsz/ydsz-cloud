/**
 * Agent 模块 - 控制器层。
 *
 * <p>对外暴露 Agent 能力的 HTTP 入口，包括：
 * <ul>
 *   <li>单 Agent 调用（如利润预测、风险预警）</li>
 *   <li>多 Agent 编排（Orchestration）</li>
 *   <li>Agent 运行轨迹查询</li>
 *   <li>Agent 配置管理</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>所有接口统一返回 {@link com.njydsz.pmis.common.api.Result}</li>
 *   <li>接口粒度按"业务能力"拆分，不与 Agent 一一对应（一个 Controller 可调用多个 Agent）</li>
 *   <li>高风险接口通过 {@code @RequireReAuth} 强制二次认证</li>
 *   <li>通过 {@code @PrePermission} 权限码控制访问</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.agent.web.controller;
