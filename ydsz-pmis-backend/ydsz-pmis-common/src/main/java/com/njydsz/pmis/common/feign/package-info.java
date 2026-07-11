/**
 * Feign 客户端统一管理层（P2-1 架构优化）。
 *
 * <p>所有微服务间 Feign 调用统一在此包定义，避免各模块各自创建 Feign 客户端导致重复。
 *
 * <h3>Feign 客户端清单</h3>
 *
 * <table border="1">
 * <caption>Feign 客户端注册表</caption>
 * <tr><th>客户端</th><th>目标服务</th><th>功能</th><th>降级</th></tr>
 * <tr><td>{@link com.njydsz.pmis.message.api.client.MessageServiceClient}</td>
 *     <td>{@link com.njydsz.pmis.common.feign.FeignClientConstants#MESSAGE}</td>
 *     <td>消息发送（多通道路由）</td>
 *     <td>MessageServiceClientFallback</td></tr>
 * <tr><td>{@link com.njydsz.pmis.message.api.client.NotificationClient}</td>
 *     <td>{@link com.njydsz.pmis.common.feign.FeignClientConstants#MESSAGE}</td>
 *     <td>通知发送 + 实时推送 + 广播</td>
 *     <td>NotificationClientFallback</td></tr>
 * <tr><td>{@link com.njydsz.pmis.userinfo.api.client.UserServiceClient}</td>
 *     <td>{@link com.njydsz.pmis.common.feign.FeignClientConstants#USER_CENTER}</td>
 *     <td>用户信息查询</td>
 *     <td>—</td></tr>
 * <tr><td>{@link com.njydsz.pmis.userinfo.api.client.OrgQueryClient}</td>
 *     <td>{@link com.njydsz.pmis.common.feign.FeignClientConstants#PROJECT}</td>
 *     <td>组织架构查询</td>
 *     <td>—</td></tr>
 * <tr><td>{@link com.njydsz.pmis.project.api.client.ExecutionClient}</td>
 *     <td>{@link com.njydsz.pmis.common.feign.FeignClientConstants#PROJECT}</td>
 *     <td>执行项查询</td>
 *     <td>—</td></tr>
 * <tr><td>{@link com.njydsz.pmis.system.api.client.ConfigClient}</td>
 *     <td>{@link com.njydsz.pmis.common.feign.FeignClientConstants#CONFIG_CENTER}</td>
 *     <td>配置中心查询</td>
 *     <td>—</td></tr>
 * <tr><td>{@link com.njydsz.pmis.agent.api.client.AgentClient}</td>
 *     <td>{@link com.njydsz.pmis.common.feign.FeignClientConstants#AGENT}</td>
 *     <td>Agent 调用（编排/工具）</td>
 *     <td>—</td></tr>
 * <tr><td>{@link com.njydsz.pmis.project.api.client.InitiationFeignClient}</td>
 *     <td>{@link com.njydsz.pmis.common.feign.FeignClientConstants#PROJECT}</td>
 *     <td>立项信息查询</td>
 *     <td>—</td></tr>
 * <tr><td>{@link com.njydsz.pmis.workflow.api.client.WorkflowServiceClient}</td>
 *     <td>{@link com.njydsz.pmis.common.feign.FeignClientConstants#WORKFLOW}</td>
 *     <td>工作流服务（启动/查询/终止流程）</td>
 *     <td>WorkflowServiceClientFallback</td></tr>
 * <tr><td>{@link com.njydsz.pmis.userinfo.api.client.BenchResourceClient}</td>
 *     <td>{@link com.njydsz.pmis.common.feign.FeignClientConstants#USERINFO}</td>
 *     <td>Bench 资源仪表盘 + 资源分配查询</td>
 *     <td>BenchResourceClientFallback</td></tr>
 * <tr><td>{@link com.njydsz.pmis.project.api.client.ProjectServiceClient}</td>
 *     <td>{@link com.njydsz.pmis.common.feign.FeignClientConstants#PROJECT}</td>
 *     <td>项目执行数据（工时异常/风险/EVM）</td>
 *     <td>ProjectServiceClientFallback</td></tr>
 * </table>
 *
 * <h3>使用规范</h3>
 * <ol>
 *   <li>新增 Feign 客户端时，统一放在 {@code common.feign} 包下</li>
 *   <li>服务名使用 {@link com.njydsz.pmis.common.feign.FeignClientConstants} 中的常量</li>
 *   <li>必须提供 {@code FallbackFactory}，确保被调方不可用时调用方主流程不受影响</li>
 *   <li>DTO 放在 {@code common.feign.dto} 包下，避免循环依赖</li>
 *   <li>不要在业务模块中重复创建指向同一服务的 Feign 客户端</li>
 * </ol>
 *
 * <h3>已迁移完成</h3>
 * <ul>
 *   <li>{@code project.feign.WorkflowServiceClient} → 已迁移到 common.feign</li>
 *   <li>{@code project.feign.BenchResourceClient} → 已迁移到 common.feign</li>
 *   <li>{@code agent.feign.ProjectServiceClient} → 已迁移到 common.feign</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P2-1)
 */
package com.njydsz.pmis.common.feign;
