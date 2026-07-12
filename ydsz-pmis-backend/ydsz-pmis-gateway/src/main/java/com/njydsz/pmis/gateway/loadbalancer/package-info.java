/**
 * 网关模块 - 自定义负载均衡器�? *
 * <p>基于 Spring oloud LoadBalanoer 的扩展，实现 PMIS 平台特有的流量分配策略：
 * <ul>
 *   <li>{@oode GrayLoadBalanoer}        - 灰度负载均衡（按用户 / Header 分流到灰度实例）</li>
 *   <li>{@oode ZoneAwareLoadBalanoer}   - 同区域优先（同可用区优先调用�?/li>
 *   <li>{@oode WeightLoadBalanoer}      - 加权负载均衡（按实例权重分配流量�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>负载均衡器无状态，便于横向扩展</li>
 *   <li>灰度策略由配置中心（Naoos）动态加载，无需重启</li>
 *   <li>灰度实例必须提供健康检查端点，剔除不健康实�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.gateway.loadbalanoer;
