package com.njydsz.pmis.agent;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.Network;

/**
 * Testcontainers 集成测试基类（P2-6 落地）。
 *
 * <p>使用真实 Docker 容器运行 PostgreSQL + Redis，替代 H2/mock，
 * 确保集成测试与生产行为一致。
 *
 * <p>对标大厂标准：所有涉及数据库/缓存的 Service 层测试应继承此类，
 * 在真实容器中验证 SQL 兼容性、事务行为、缓存一致性。
 *
 * <p>使用方式：
 * <pre>{@code
 * @SpringBootTest
 * class MyServiceIT extends AbstractIntegrationTest {
 *     @Autowired
 *     private MyService myService;
 *
 *     @Test
 *     void testSomething() {
 *         // 使用真实 PostgreSQL + Redis
 *     }
 * }
 * }</pre>
 *
 * <p>注意：需要本地安装 Docker，CI 环境已预装 Docker。
 *
 * @author ydsz-pmis-team
 * @since 1.3.1 (P2-6)
 */
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    /** 共享网络（让容器间可互相访问） */
    static final Network NETWORK = Network.SHARED;

    /** PostgreSQL 容器（与生产环境一致：PostgreSQL 18） */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres")
            .withDatabaseName("pmis_test")
            .withUsername("pmis")
            .withPassword("pmis_test")
            .withReuse(true);

    /** Redis 容器（与生产环境一致：Redis 8） */
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8-alpine"))
            .withNetwork(NETWORK)
            .withNetworkAliases("redis")
            .withExposedPorts(6379)
            .withReuse(true);

    /**
     * 动态注入数据源和 Redis 配置（使用容器随机端口）。
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL 数据源
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // 禁用 Nacos（测试环境不需要服务发现）
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");

        // 禁用 Seata（测试环境不需要分布式事务协调器）
        registry.add("seata.enabled", () -> "false");

        // 禁用 Sentry
        registry.add("pmis.sentry.enabled", () -> "false");
    }
}
