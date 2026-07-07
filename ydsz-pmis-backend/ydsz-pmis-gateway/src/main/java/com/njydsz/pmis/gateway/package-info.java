/**
 * PMIS API 网关模块（ydsz-pmis-gateway）。
 *
 * <p>基于 Spring Cloud Gateway 实现的统一 API 网关，是 PMIS 平台的"流量总入口"。
 * 网关承担路由、鉴权、限流、跨域、内部头注入、灰度等横切能力，业务模块（system / userinfo / project /
 * workflow / agent）专注于业务实现。
 *
 * <h3>包结构</h3>
 * <ul>
 *   <li>{@code config}       - 网关路由 / 跨域 / 安全配置</li>
 *   <li>{@code filter}       - 全局过滤器（鉴权 / 限流 / 内部头 / 灰度）</li>
 *   <li>{@code loadbalancer} - 自定义负载均衡器（灰度 / 区域路由）</li>
 * </ul>
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li>统一鉴权：JWT 解析 + 用户信息透传（{@code X-User-*} Headers）</li>
 *   <li>统一限流：基于 IP / 用户 / 路径的多维度限流</li>
 *   <li>统一跨域：处理 CORS 预检 / 跨域头注入</li>
 *   <li>统一灰度：基于用户 / 部门 / Header 的灰度流量分配</li>
 *   <li>统一内部头签名：注入 {@code X-Internal-Sig} 防网关绕过</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>网关不执行业务逻辑，仅做请求的"预处理"与"后处理"</li>
 *   <li>所有过滤器必须是无状态的，便于横向扩展</li>
 *   <li>过滤器链顺序通过 {@code @Order} 显式声明</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.gateway;
