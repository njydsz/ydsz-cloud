package com.njydsz.pmis.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * Web 端 Session 共享自动配置
 *
 * <p>基于 Spring Session + Redis 实现分布式 Session 共享。
 * 仅在 classpath 中存在 spring-session-data-redis 且配置启用时生效。
 *
 * <p><b>前置条件：</b>
 * <ol>
 *   <li>添加依赖：{@code spring-session-data-redis}</li>
 *   <li>配置启用：{@code remi.web.session.enabled=true}</li>
 *   <li>Redis 已正确配置</li>
 * </ol>
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * remi:
 *   web:
 *     session:
 *       enabled: true
 * server:
 *   servlet:
 *     session:
 *       timeout: 30m
 * spring:
 *   session:
 *     redis:
 *       namespace: remi:session
 *       flush-mode: on_save
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession")
@ConditionalOnProperty(prefix = "remi.web.session", name = "enabled", havingValue = "true")
@Import(RedisHttpSessionImportSelector.class)
public class WebSessionAutoConfiguration {
}