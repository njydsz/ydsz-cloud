package com.njydsz.system.infra._support;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers 集成测试基类（云顶编码规范 §14.3 集成测试）。
 *
 * <p>提供：
 *
 * <ul>
 *   <li>PostgreSQL 16 容器（单 JVM 共享一个静态容器，复用节省启动时间）</li>
 *   <li>{@link DynamicPropertySource} 自动注入到 Spring Environment</li>
 *   <li>{@code @ActiveProfiles("test")} 加载 application-test.yml</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * @SpringBootTest(classes = SystemInfraTestApplication.class)
 * class ConfigRepositoryIT extends SystemTestInitializer {
 *   @Autowired private ConfigRepository configRepository;
 *   // ...
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public abstract class SystemTestInitializer {

  /** PostgreSQL 16 容器 — 整个 JVM 生命周期内只启动一次，所有 IT 用例共享 */
  @Container
  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
          .withDatabaseName("ydsz_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(true);

  /**
   * 将 Testcontainers 的连接信息注入到 Spring Environment。
   *
   * <p>ydsz-common-jdbc 的 DynamicDataSourceAutoConfiguration 将读取这些属性并构建 DataSource。
   *
   * @param registry Spring Boot 动态属性注册表
   */
  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("ydsz.jdbc.mapper-scan-packages", () -> "com.njydsz.system.infra.mapper");
    registry.add("ydsz.jdbc.dynamic-datasource.enabled", () -> "true");
  }
}
