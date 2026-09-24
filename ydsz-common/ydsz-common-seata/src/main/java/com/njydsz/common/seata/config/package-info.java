/**
 * Seata 自动配置与属性绑定。
 *
 * <p>门控开关 {@code ydsz.seata.enabled} 由 {@link
 * com.njydsz.common.seata.config.SeataAutoConfiguration} 上的
 * {@code @ConditionalOnProperty} 控制；客户端自身的 TC 地址、vgroup 等
 * 仍由 Seata 标准 {@code seata.*} 前缀配置。
 *
 * @since ACC-1
 */
package com.njydsz.common.seata.config;
