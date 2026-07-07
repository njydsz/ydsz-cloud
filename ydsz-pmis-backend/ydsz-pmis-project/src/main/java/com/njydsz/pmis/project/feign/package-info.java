/**
 * 跨服务 Feign 客户端层。
 *
 * <p>本包定义项目模块对外发起的所有 Feign RPC 调用，目标是其他微服务（userinfo、execution、workflow、
 * message、bench 等）。每个 Client 必须配套 Fallback 实现，避免下游不可用时拖垮主业务。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.feign.UserServiceClient} - 用户/客户/员工信息服务（含 Fallback）</li>
 *   <li>{@link com.njydsz.pmis.project.feign.InitiationServiceClient} - 立项服务（用于预算查询）</li>
 *   <li>{@link com.njydsz.pmis.project.feign.MessageServiceClient} - 消息中心服务（告警/通知）</li>
 *   <li>{@link com.njydsz.pmis.project.feign.WorkflowServiceClient} - 工作流服务（审批流）</li>
 *   <li>{@link com.njydsz.pmis.project.feign.BenchResourceClient} - Bench 资源服务（人员分配/成本）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>必须配 Fallback</b>：每个 {@code @FeignClient} 必须显式配置 {@code fallbackFactory}，禁止裸用</li>
 *   <li><b>降级有据</b>：Fallback 返回值必须合理（如空集合、空字符串、零值），不允许直接抛异常</li>
 *   <li><b>超时收敛</b>：connectTimeout / readTimeout 通过 {@code application.yml} 统一配置，本包不重复声明</li>
 *   <li><b>接口集中</b>：本包只放 Feign 接口，不放 Feign 实现（实现由 fallback 工厂承担）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>调用方必须做好二次判空（即使配了 Fallback，{@code Result.data} 也可能为 null）</li>
 *   <li>跨服务调用禁止在事务内阻塞主流程，必要时使用 {@code @Async} 异步化</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.feign;
