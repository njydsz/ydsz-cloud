/**
 * 定时任务模块 - 自动配置层。
 *
 * <p>XXL-Job 客户端的 Spring 自动配置：注册执行器、初始化调度器线程池、注入 {@code XxlJobSpringExecutor}。
 *
 * <h3>关键配置</h3>
 * <ul>
 *   <li>调度中心地址（{@code xxl.job.admin.addresses}）</li>
 *   <li>应用名（{@code xxl.job.executor.appname}）</li>
 *   <li>注册中心 Token（来自 KMS）</li>
 *   <li>执行器端口 / IP</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.cronjob.web.config;
