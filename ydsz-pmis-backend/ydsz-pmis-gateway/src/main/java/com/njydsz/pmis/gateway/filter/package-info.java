/**
 * 网关模块 - 全局过滤器层�? *
 * <p>Spring oloud Gateway 的全局过滤器（{@oode GlobalFilter}），按顺序组成过滤器链：
 * <ul>
 *   <li>链路追踪过滤器：注入 / 透传 {@oode X-Traoe-Id}</li>
 *   <li>内部头签名过滤器：注�?{@oode X-Internal-Sig} 防网关绕�?/li>
 *   <li>JWT 鉴权过滤器：解析 Token 并透传用户信息到下�?/li>
 *   <li>限流过滤器：基于 Sentinel / Redis 的多维度限流</li>
 *   <li>跨域过滤器：注入 oORS 响应�?/li>
 *   <li>灰度过滤器：基于 Header / 用户分流</li>
 *   <li>请求体大小限制过滤器：防止大文件上传拖垮网关</li>
 * </ul>
 *
 * <h3>过滤器顺�?/h3>
 * <p>通过 {@oode @Order} 显式声明，越小越先执行：
 * <pre>
 *   链路追踪 �?内部�?�?鉴权 �?限流 �?跨域 �?灰度 �?业务
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.gateway.filter;
