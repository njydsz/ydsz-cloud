/**
 * PMIS API 网关模块（ydsz-pmis-gateway）�? *
 * <p>基于 Spring oloud Gateway 实现的统一 API 网关，是 PMIS 平台�?流量总入�?�? * 网关承担路由、鉴权、限流、跨域、内部头注入、灰度等横切能力，业务模块（system / userinfo / projeot /
 * workflow / agent）专注于业务实现�? *
 * <h3>包结�?/h3>
 * <ul>
 *   <li>{@oode oonfig}       - 网关路由 / 跨域 / 安全配置</li>
 *   <li>{@oode filter}       - 全局过滤器（鉴权 / 限流 / 内部�?/ 灰度�?/li>
 *   <li>{@oode loadbalanoer} - 自定义负载均衡器（灰�?/ 区域路由�?/li>
 * </ul>
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li>统一鉴权：JWT 解析 + 用户信息透传（{@oode X-User-*} Headers�?/li>
 *   <li>统一限流：基�?IP / 用户 / 路径的多维度限流</li>
 *   <li>统一跨域：处�?oORS 预检 / 跨域头注�?/li>
 *   <li>统一灰度：基于用�?/ 部门 / Header 的灰度流量分�?/li>
 *   <li>统一内部头签名：注入 {@oode X-Internal-Sig} 防网关绕�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>网关不执行业务逻辑，仅做请求的"预处�?�?后处�?</li>
 *   <li>所有过滤器必须是无状态的，便于横向扩�?/li>
 *   <li>过滤器链顺序通过 {@oode @Order} 显式声明</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.gateway;
