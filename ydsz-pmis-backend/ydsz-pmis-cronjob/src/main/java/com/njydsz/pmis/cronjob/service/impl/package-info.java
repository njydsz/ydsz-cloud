/**
 * 定时任务模块 - 业务服务实现层。
 *
 * <p>{@code service} 包下接口的具体实现，命名规范：{@code <接口名>Impl}。
 * 实现类统一加 {@code @Service} 注解，事务管理由 {@code @Transactional} 显式声明。
 *
 * <h3>实现约束</h3>
 * <ul>
 *   <li>所有 Service 实现必须包含单元测试（覆盖正常 / 异常 / 边界场景）</li>
 *   <li>XXL-Job API 调用需要异常重试（任务调度网络抖动）</li>
 *   <li>任务执行超时需记录告警（通过 {@code JobRunRecorder}）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.cronjob.service.impl;
