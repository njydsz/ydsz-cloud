package com.njydsz.system.server;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * System 模块集成测试基类。
 *
 * <p>提供 PostgreSQL 18 + Redis 7 Testcontainers 真实环境支撑，所有继承此类的集成测试方法共享同一套容器实例。
 *
 * <p><b>使用前提：</b>运行环境需安装 Docker（Testcontainers 通过 Docker 拉取镜像并启动容器）。
 *
 * <p><b>数据隔离：</b>每个测试类运行完毕后应在 {@code @AfterEach} 中清理业务数据，避免相互污染。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@SpringBootTest(classes = TestSystemApplication.class)
// disabledWithoutDocker：无 Docker 的开发机上让本类以"可见跳过"结束，而不是抛
// Could not find a valid Docker environment 变成红灯；CI 镜像内 Docker 可用时照常执行。
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

  /** PostgreSQL 18 测试容器（与生产环境保持一致） */
  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:18-alpine")
          .withDatabaseName("ydsz_test")
          .withUsername("test")
          .withPassword("test");

  /** Redis 7 测试容器（与生产环境保持一致） */
  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  /**
   * 动态注册 Spring 属性：将 Testcontainers 的连接信息注入到 Spring Environment。
   *
   * <p>覆盖 application-test.yml 中的默认连接配置，使用 Testcontainers 实际的动态端口。
   *
   * @param registry Spring 动态属性注册器
   */
  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add(
        "spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add(
        "spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
  }
}
