package com.njydsz.pmis.common.core.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试抽象基类（P1-7：Testcontainers 基础设施）
 *
 * <p>所有需要真实 PostgreSQL 数据库的集成测试继承此类，
 * 自动启动 PG 容器并注入 DataSource 连接信息。
 *
 * <p>使用前提：本地或 CI 环境需安装 Docker。
 *
 * <p>用法示例：
 * <pre>{@code
 * @SpringBootTest
 * class MyServiceIntegrationTest extends AbstractIntegrationTest {
 *     @Autowired
 *     private MyMapper myMapper;
 *
 *     @Test
 *     void shouldInsertAndQuery() {
 *         myMapper.insert(entity);
 *         assertThat(myMapper.selectById(id)).isNotNull();
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    /** PostgreSQL 18 容器（与生产环境版本一致） */
    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("pmis_test")
            .withUsername("pmis")
            .withPassword("pmis")
            .withReuse(true);

    /**
     * 注入 DataSource 连接信息到 Spring 上下文
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }
}
