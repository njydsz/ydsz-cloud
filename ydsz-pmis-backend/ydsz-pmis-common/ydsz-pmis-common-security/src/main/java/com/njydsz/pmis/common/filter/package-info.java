/**
 * Web 过滤器层。
 *
 * <p>所有 Servlet Filter 集中在本包注册。过滤器是 Spring 容器中最早期的拦截点，
 * 适用于跨服务关注点：XSS 清洗、链路追踪 ID 注入、CSRF Cookie 严格化、请求体 Content-Type 校验等。
 *
 * <h3>过滤器清单（按 FilterRegistrationBean 显式声明的顺序）</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.filter.TraceIdFilter}          - 链路追踪 ID 注入 / 透传
 *                                                                     （与 Brave {@code Slf4jCurrentTraceContext} 协同）</li>
 *   <li>{@link com.njydsz.pmis.common.filter.XssFilter}             - XSS 清洗（基于 Jsoup 白名单）</li>
 *   <li>{@link com.njydsz.pmis.common.filter.SameSiteCookieFilter}  - 强制 Cookie {@code SameSite=Lax} 防 CSRF</li>
 *   <li>{@link com.njydsz.pmis.common.filter.StrictContentTypeFilter} - 拒绝非预期 Content-Type 的请求体解析</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>过滤器顺序通过 {@code FilterRegistrationBean.setOrder} 显式声明</li>
 *   <li>过滤器仅处理通用场景，业务校验统一在拦截器 / AOP / Controller 层</li>
 *   <li>所有过滤器对 {@code /actuator/**}、静态资源放行</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.filter;
