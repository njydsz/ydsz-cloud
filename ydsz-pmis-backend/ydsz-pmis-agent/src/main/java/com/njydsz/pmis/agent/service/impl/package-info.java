/**
 * Agent 模块 - 业务服务实现层。
 *
 * <p>{@code service} 包下接口的具体实现，命名规范：{@code <接口名>Impl}。
 * 实现类统一加 {@code @Service} 注解，事务管理由 {@code @Transactional} 显式声明。
 *
 * <h3>实现约束</h3>
 * <ul>
 *   <li>实现类禁止跨模块直接访问数据库，必须通过 Feign 客户端或 Domain Event</li>
 *   <li>实现类方法必须包含单元测试（覆盖正常 / 异常 / 边界场景）</li>
 *   <li>LLM 调用通过 {@code LlmProviderRouter} 获取 Provider，禁止直接注入具体实现</li>
 *   <li>Agent 运行结果异步落库（{@code AgentPredictionMapper}），不阻塞返回</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.agent.service.impl;
