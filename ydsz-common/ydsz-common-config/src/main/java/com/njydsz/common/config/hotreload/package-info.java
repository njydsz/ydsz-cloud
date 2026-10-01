/**
 * 配置热重载桥接（ConfigChangeBridge）——Spring Cloud 中心配置变更的 diff 分发引擎。
 *
 * <p><b>本包定位：</b>监听 Spring Cloud {@code RefreshEvent} / {@code EnvironmentChangeEvent}，
 * 对 Nacos / Apollo / Spring Cloud Config 下发的 <b>扁平 key-value 属性</b>做变更 diff，
 * 并通过 {@link com.njydsz.common.config.hotreload.ConfigChangeListener} SPI 分发给各业务模块的监听器实现。
 *
 * <p><b>与 literule 规则热加载的边界说明：</b>
 *
 * <table border="1" cellpadding="3">
 *   <tr><th>维度</th><th>本包（ydsz-common-config）</th><th>literule 规则热加载</th></tr>
 *   <tr>
 *     <td>变更来源</td>
 *     <td>Spring Cloud Environment（Nacos / Apollo / Spring Cloud Config）</td>
 *     <td>业务数据库（规则 DSL 的 CRUD）+ Redis Pub/Sub</td>
 *   </tr>
 *   <tr>
 *     <td>数据格式</td>
 *     <td>扁平 key-value（{@code ydsz.module.property} = value）</td>
 *     <td>结构化业务实体（RuleDefinition / DecisionTable）</td>
 *   </tr>
 *   <tr>
 *     <td>事件机制</td>
 *     <td>{@code RefreshEvent} / {@code EnvironmentChangeEvent}</td>
 *     <td>{@code RuleConfigRefreshEvent} → Redis Pub/Sub → {@code CachingRuleConfigProvider}</td>
 *   </tr>
 *   <tr>
 *     <td>监听器接口</td>
 *     <td>{@link com.njydsz.common.config.hotreload.ConfigChangeListener}</td>
 *     <td>{@code RuleHotReloader} → {@code RuleConfigChangeListener}（业务内部）</td>
 *   </tr>
 *   <tr>
 *     <td>回调签名</td>
 *     <td>{@code onChange(String key, String oldValue, String newValue)}</td>
 *     <td>{@code reloadRule(String ruleCode, RuleDefinition newDefinition)}</td>
 *   </tr>
 *   <tr>
 *     <td>配置键示例</td>
 *     <td>{@code ydsz.cronjob.scheduler-pool-size}</td>
 *     <td>N/A（规则实体无扁平 key 概念）</td>
 *   </tr>
 * </table>
 *
 * <p><b>与 TenantConfigProvider 的边界说明：</b>
 *
 * <p>{@code ydsz-common-tenant} 的 {@code TenantConfigProvider} 支持运行时动态覆盖（Map 结构，in-memory），
 * 不与 Spring Cloud 配置中心联动。本包的 {@code ConfigChangeBridge} 不感知 TenantConfigProvider 的覆盖逻辑，
 * 租户覆盖通过 {@code TenantConfigProvider.get(key, defaultValue)} 运行时优先级仲裁。
 *
 * <p><b>与 JSON ConfigChangeListener（ydsz-common-json）的边界说明：</b>
 *
 * <p>{@code JsonConfig.JsonConfigChangeListener} 是 JSON 引擎内部 API，响应 {@code JsonConfig.install()}
 * 配置安装事件（命名策略、日期格式等 JSON 全局配置）。两者事件源和消费接口完全不同，
 * 独立注册、互不干扰。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
package com.njydsz.common.config.hotreload;
