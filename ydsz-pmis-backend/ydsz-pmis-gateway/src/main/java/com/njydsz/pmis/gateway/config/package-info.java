/**
 * 网关模块 - 自动配置层。
 *
 * <p>Spring Cloud Gateway 的自动配置：路由规则、跨域（CORS）、HTTPS、Actuator 暴露等。
 *
 * <h3>关键配置</h3>
 * <ul>
 *   <li>路由规则：从 Nacos 动态加载（支持热更新）</li>
 *   <li>跨域：开发 / 生产环境分别配置允许的 Origin</li>
 *   <li>HTTPS：生产环境强制 HTTPS（HTTP 跳转）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.gateway.config;
