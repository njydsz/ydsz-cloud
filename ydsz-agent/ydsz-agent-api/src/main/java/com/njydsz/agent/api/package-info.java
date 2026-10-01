/**
 * Agent 模块 API 层（契约与 Feign 客户端）。
 *
 * <h3>跨模块 Feign 调用</h3>
 *
 * <p>本子模块定义 {@code client} 子包（{@link com.njydsz.agent.api.client}）下的 Feign 客户端契约接口，
 * 以及其他后端模块调用 Agent 引擎的统一入口。当前已启用：
 *
 * <ul>
 *   <li>{@link com.njydsz.agent.api.client.AgentExecuteClient} — 同步执行 Agent（{@code POST /api/agent/execute}），
 *       供 workflow / 其他审批流调用</li>
 * </ul>
 *
 * <h3>跨模块调用路径</h3>
 *
 * <ul>
 *   <li>前端 BFF → Gateway → agent-web：HTTP REST 直调</li>
 *   <li>workflow 模块 → {@link com.njydsz.agent.api.client.AgentExecuteClient} Feign → agent-web</li>
 * </ul>
 *
 * <h3>新增 Feign 客户端步骤</h3>
 *
 * <ol>
 *   <li>在 {@code client} 子包新建 {@code @FeignClient} 接口（name = {@code FeignClientConstants.SERVICE_AGENT}）</li>
 *   <li>在 {@code fallback} 子包新建对应 {@code FallbackFactory} 实现（委托 {@code I18n.message}）</li>
 *   <li>返回类型必须使用 {@code YdszResponse<T>} 以利用 {@code ResponseUnwrapDecoder}</li>
 * </ol>
 *
 * <h3>模块依赖约束</h3>
 *
 * <ul>
 *   <li>本模块依赖 {@code ydzs-agent-domain} (provided) + {@code ydsz-common-feign} + {@code ydsz-common-core}</li>
 *   <li>禁止引入 infra / server / web 层依赖</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.10.01 新增 AgentExecuteClient Feign 契约（P1-3 整改）
 */
package com.njydsz.agent.api;
