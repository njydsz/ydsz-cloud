/**
 * AOP 切面层。
 *
 * <p>所有横切关注点（权限 / 限流 / 幂等 / 分布式锁 / 数据范围 / 操作日志 / 接口指标 / 敏感操作）
 * 均通过 AOP 切面实现，扫描 {@code com.njydsz.pmis.common.annotation} 包下的自定义注解。
 * 切面由 {@link com.njydsz.pmis.common.config.CommonAutoConfiguration} 统一注册，
 * 并通过 {@code @ConditionalOnMissingBean} 保证可被业务模块按需覆盖。
 *
 * <h3>切面清单</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.aspect.ApiMetricsAspect}      - 接口 QPS / 延迟 / 错误率埋点（Micrometer）</li>
 *   <li>{@link com.njydsz.pmis.common.aspect.DataExportAuditAspect} - 数据导出审计（异步落库 + 异步通道）</li>
 *   <li>{@link com.njydsz.pmis.common.aspect.DataScopeAspect}       - 数据范围过滤（基于 MyBatis-Plus 拦截器）</li>
 *   <li>{@link com.njydsz.pmis.common.aspect.DistributedLockAspect} - 分布式锁（Redis SETNX + 业务异常自动释放）</li>
 *   <li>{@link com.njydsz.pmis.common.aspect.IdempotentAspect}      - 幂等控制（Lua 原子脚本）</li>
 *   <li>{@link com.njydsz.pmis.common.aspect.OperationLogAspect}    - 操作日志（通过 {@code ApplicationEventPublisher} 发布事件，监听器异步落库）</li>
 *   <li>{@link com.njydsz.pmis.common.aspect.PermissionAspect}     - 权限码校验（@PrePermission）</li>
 *   <li>{@link com.njydsz.pmis.common.aspect.RateLimiterAspect}     - 限流（注解方式）</li>
 *   <li>{@link com.njydsz.pmis.common.aspect.RequireReAuthAspect}   - 二次认证校验</li>
 * </ul>
 *
 * <h3>实现约束</h3>
 * <ul>
 *   <li>切面顺序通过 {@code @Order} 显式声明，避免隐式排序导致行为不可预期</li>
 *   <li>幂等 / 分布式锁释放逻辑：业务异常时自动释放，避免锁泄漏</li>
 *   <li>操作日志切面必须依赖 {@code ApplicationEventPublisher}，不得直接调用 Mapper</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.aspect;
