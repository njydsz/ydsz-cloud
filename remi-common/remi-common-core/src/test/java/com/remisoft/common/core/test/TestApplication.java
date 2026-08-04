package com.remisoft.common.core.test;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 集成测试的最小 Spring Boot 应用入口。
 *
 * <p>仅用于为 {@code @SpringBootTest} 提供上下文，无任何业务逻辑。
 * 数据源与 Redis 连接属性由 {@link AbstractIntegrationTest} 的 static 块通过
 * {@code System.setProperty} 注入。</p>
 *
 * @since 1.0.0
 */
@SpringBootApplication
public class TestApplication {
    // 纯占位——只为 @SpringBootTest 提供 SpringApplication 类
}
