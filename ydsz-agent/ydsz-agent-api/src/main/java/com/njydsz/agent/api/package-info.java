/**
 * Agent 模块 API 层（契约与 Feign 客户端）.
 *
 * <h3>当前设计决策</h3>
 * <p>Agent 子系统当前以独立微服务形态对外暴露 REST 端点，前端通过网关直接消费，
 * 暂无其他后端模块依赖 Agent 的 RPC 场景。因此本模块暂不定义 FeignClient 接口，
 * 仅作为契约层预留，便于未来跨服务调用场景引入 Feign 客户端时保持架构一致。</p>
 *
 * <h3>跨模块调用路径</h3>
 * <ul>
 *   <li>前端 BFF --{@literal >} Gateway --{@literal >} agent-web：HTTP REST 直调</li>
 *   <li>其他后端模块 --{@literal >} 暂未引入 Feign 依赖，按需启用</li>
 * </ul>
 *
 * <h3>未来演进</h3>
 * <p>当其他模块需要通过 Feign 调用 Agent 能力时：</p>
 * <ol>
 *   <li>在本模块新建 {@code client} 子包，定义 {@code @FeignClient} 接口</li>
 *   <li>创建 {@code fallback} 子包，提供降级实现</li>
 *   <li>引入 {@code ydsz-agent-domain} 依赖，复用 {@code dto/vo} 定义</li>
 * </ol>
 *
 * <h3>模块依赖约束</h3>
 * <ul>
 *   <li>本模块仅依赖 {@code ydsz-agent-domain} + {@code ydsz-common-feign}（按需）</li>
 *   <li>禁止引入 infra / server / web 层依赖</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.agent.api;
