/**
 * Agent 模块 - 业务服务接口层。
 *
 * <p>对外提供的服务接口（与 {@code controller} 配套）：
 * <ul>
 *   <li>{@code AgentService}            - 单 Agent 调用入口</li>
 *   <li>{@code AgentOrchestrationService} - 多 Agent 编排入口</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>Service 接口与实现分离（实现放 {@code service\impl} 子包）</li>
 *   <li>Service 方法命名采用"业务动作"风格（{@code runXxx} / {@code predictXxx}）</li>
 *   <li>Service 方法必须显式声明事务边界（{@code @Transactional}）</li>
 *   <li>Service 之间不互相调用 Mapper（保证数据访问在 Service 层聚合）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.agent.service;
