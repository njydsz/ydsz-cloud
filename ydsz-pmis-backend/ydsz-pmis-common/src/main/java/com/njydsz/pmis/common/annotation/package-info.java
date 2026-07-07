/**
 * 自定义注解层。
 *
 * <p>集中定义所有业务 / 平台自定义注解，配合 {@code com.njydsz.pmis.common.aspect} 包下的 AOP 切面
 * 实现统一拦截。注解采用"声明式"风格，业务代码通过添加注解即可获得对应能力（幂等、限流、权限、
 * 数据范围、操作日志、API 指标、分布式锁、敏感字段、二次认证等），无需侵入业务逻辑。
 *
 * <h3>注解清单</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.annotation.ApiMetrics}      - 接口调用埋点（P0-QPS / 延迟）</li>
 *   <li>{@link com.njydsz.pmis.common.annotation.DataExportAudit} - 数据导出审计（合规）</li>
 *   <li>{@link com.njydsz.pmis.common.annotation.DataScope}       - 数据范围隔离</li>
 *   <li>{@link com.njydsz.pmis.common.annotation.DistributedLock} - 分布式锁（Redis）</li>
 *   <li>{@link com.njydsz.pmis.common.annotation.Idempotent}      - 幂等控制（Redis SETNX + 滑动窗口）</li>
 *   <li>{@link com.njydsz.pmis.common.annotation.OperationLog}    - 操作日志（异步落库）</li>
 *   <li>{@link com.njydsz.pmis.common.annotation.PrePermission}   - 权限码前置校验</li>
 *   <li>{@link com.njydsz.pmis.common.annotation.RateLimit}       - 限流（Sentinel / Resilience4j）</li>
 *   <li>{@link com.njydsz.pmis.common.annotation.RequireReAuth}   - 二次认证（高敏操作）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.annotation;
