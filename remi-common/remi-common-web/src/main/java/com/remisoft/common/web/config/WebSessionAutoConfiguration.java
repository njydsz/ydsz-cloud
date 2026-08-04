package com.remisoft.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * Web 端 Session 自动配置。
 *
 * <p>封装 Spring Session + Redis 集群的 Session 共享配置：连接工厂、序列化器、过期时间、刷新策略。
 *
 * <p>通过 {@code remi.web.session.enabled=false} 可降级为本地 Session。
 *
 * @author remi-team
 * @since 1.0.0
 */

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession")
@ConditionalOnProperty(prefix = "remi.web.session", name = "enabled", havingValue = "true")
@Import(RedisHttpSessionImportSelector.class)
public class WebSessionAutoConfiguration {
}
