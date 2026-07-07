/**
 * 规则引擎 - 分布式规则调用层。
 *
 * <p>支持"跨服务规则调用"：当规则所需数据需要从其他微服务获取时，通过 Feign 客户端拉取。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code DistributedRuleClient} - 分布式规则客户端</li>
 *   <li>{@code RuleDataLoader}        - 规则所需数据加载器（SPI 扩展）</li>
 *   <li>{@code CrossServiceRule}      - 跨服务规则抽象</li>
 * </ul>
 *
 * <h3>使用约束</h3>
 * <ul>
 *   <li>分布式规则调用必须配置超时（默认 2s）</li>
 *   <li>支持降级：远程服务不可用时回退到本地规则</li>
 *   <li>调用链追踪：远程规则 traceId 透传</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.literule.distributed;
