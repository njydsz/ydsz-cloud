package com.njydsz.common.web.config;

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
 * <p>通过 {@code ydsz.web.session.enabled=false} 可降级为本地 Session。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 注解/反射类名字符串常量，非代码引用
@ConditionalOnClass(
    name =
        "org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession")
  // CHECKSTYLE.ON: RegexpSinglelineJava
@ConditionalOnProperty(prefix = "ydsz.web.session", name = "enabled", havingValue = "true")
@Import(RedisHttpSessionImportSelector.class)
/**
 * Web 会话自动配置
 *
 * <p>启用 Redis HTTP 会话管理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class WebSessionAutoConfiguration {}
