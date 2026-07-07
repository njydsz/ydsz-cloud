/**
 * 事件监听器层：异步消费 Spring 事件总线中的业务事件并落库审计。
 *
 * <p>本包基于 Spring {@code @EventListener} + {@code @Async} 实现事件驱动审计，
 * 主业务流程发布事件后立即返回，监听器在独立线程池中完成落库，不影响接口 RT。
 * 通过"重试 + Fallback 补偿"双保险机制，保证审计数据零丢失。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code OperationLogListener} - 操作日志监听器，监听 {@code OperationLogEvent}，
 *       落库 {@code pmis_operation_log}；失败重试 1 次（100ms）后仍失败则转 Fallback 文件补偿</li>
 *   <li>{@code LoginAuditListener} - 登录审计监听器，记录登录成功/失败/IP/UA 等信息</li>
 *   <li>{@code DataExportAuditListener} - 数据导出审计监听器，记录导出人/范围/行数/审批单号</li>
 *   <li>{@code SensitiveOperationListener} - 敏感操作监听器（如大额审批、权限变更），
 *       落库 {@code pmis_sensitive_operation} 用于后续合规审计</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>异步解耦</b>：所有监听器均标注 {@code @Async}，绑定独立线程池（如 {@code auditExecutor}），
 *       与 Web 请求线程隔离</li>
 *   <li><b>失败兜底</b>：监听器内部对所有异常 {@code try-catch}，禁止向上抛出影响主流程；
 *       配合 {@code fallback} 包实现"日志 → 文件"双保险</li>
 *   <li><b>事件轻量</b>：事件对象仅承载关键字段（traceId、模块、动作、用户、状态），
 *       避免传输大对象（如完整请求体）</li>
 *   <li><b>重试有限度</b>：仅对瞬时故障重试 1 次（间隔 100ms），超过后立即降级，
 *       避免长时间占用线程池</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增监听器须在 {@code package-info.java} 中登记，并配套 {@code fallback} 实现</li>
 *   <li>监听器方法禁止标注 {@code @Transactional}（独立线程不受外层事务控制，
 *       应使用独立事务或编程式事务）</li>
 *   <li>事件发布使用 {@code ApplicationEventPublisher.publishEvent(event)}，
 *       默认走同步路径；如需异步在事件类上标注 {@code @Async}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.listener;
