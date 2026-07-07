/**
 * Spring / 第三方组件自动配置层。
 *
 * <p>本包集中注册所有"平台级"自动配置类（{@code @Configuration}），避免分散在业务模块。
 * 业务模块引入 {@code ydsz-pmis-common} 后即可获得：
 * <ul>
 *   <li>MyBatis-Plus 配置（分页插件、租户行级隔离、审计字段自动填充）</li>
 *   <li>Redis 缓存配置（多 CacheManager、TTL、序列化器）</li>
 *   <li>Sentinel 限流规则动态加载</li>
 *   <li>Seata 分布式事务客户端</li>
 *   <li>Resilience4j 熔断器</li>
 *   <li>异步线程池（审计 / 导出 / Agent）</li>
 *   <li>OpenAPI 文档 / I18N 消息源 / WebMvc 增强 / 布隆过滤器</li>
 *   <li>Druid 监控 / Sentinel 自动装配</li>
 * </ul>
 *
 * <h3>配置约定</h3>
 * <ul>
 *   <li>所有配置类通过 {@code @ConditionalOnClass} / {@code @ConditionalOnProperty} 按需激活，
 *       业务模块禁用某能力时无需排除包</li>
 *   <li>用户可覆盖的 Bean 一律标注 {@code @ConditionalOnMissingBean}</li>
 *   <li>配置类顺序通过 {@code @AutoConfigureOrder} 显式声明</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.config;
