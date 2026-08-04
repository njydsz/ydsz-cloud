package com.njydsz.common.core.test;

import org.springframework.boot.test.autoconfigure.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基类（P0-9 修复）
 *
 * <p>基于 Testcontainers 自动启动 PostgreSQL 和 Redis 容器，供集成测试使用。
 * 子类继承此基类后，容器会在测试类启动时自动创建，测试结束后自动销毁。
 *
 * <h3>用法</h3>
 * <pre>
 * public class MyServiceIT extends AbstractIntegrationTest {
 *     &#64;Autowired
 *     private MyService myService;
 *
 *     &#64;Test
 *     void testSomething() {
 *         // PostgreSQL 在 localhost:随机端口 可用
 *         // Redis 在 localhost:随机端口 可用
 *     }
 * }
 * </pre>
 *
 * @since 1.0.0
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /** PostgreSQL 容器（PostgreSQL 16） */
    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ydsz_test")
            .withUsername("test")
            .withPassword("test");

    /** Redis 容器（Redis 7-alpine） */
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        // 设置 Spring DataSource 属性指向 Testcontainers
        System.setProperty("spring.datasource.url", POSTGRESQL.getJdbcUrl());
        System.setProperty("spring.datasource.username", POSTGRESQL.getUsername());
        System.setProperty("spring.datasource.password", POSTGRESQL.getPassword());
        System.setProperty("spring.data.redis.host", REDIS.getHost());
        System.setProperty("spring.data.redis.port", String.valueOf(REDIS.getMappedPort(6379)));
    }
}
