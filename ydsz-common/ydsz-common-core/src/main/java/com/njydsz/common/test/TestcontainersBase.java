package com.njydsz.common.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基类 — PostgreSQL + Redis Testcontainers 容器。
 *
 * <p>继承本基类的集成测试将自动启动：
 *
 * <ul>
 *   <li>PostgreSQL 18 容器（{@code postgres:18-alpine}），JDBC URL 通过 {@code spring.datasource.url} 注册
 *   <li>Redis 7 容器（{@code redis:7-alpine}），连接信息通过 {@code spring.data.redis.*} 注册
 * </ul>
 *
 * <p>子类需使用 {@code @SpringBootTest} 并指向对应的 Application 启动类，
 * 同时通过 {@code @TestPropertySource} 禁用 Nacos 配置中心与注册中心。
 *
 * <p>Testcontainers 状态（容器实例、JDBC URL）由 JUnit 5 的 {@code @Testcontainers} 管理，
 * 在所有测试间共享（static 容器，启动一次、所有测试共用）。
 *
 * <p>数据回滚：子类测试方法建议添加 {@code @Transactional}，
 * 每次测试结束后 Spring 自动回滚事务，保证测试隔离。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Testcontainers
@ActiveProfiles("test")
public abstract class TestcontainersBase {

  private static final Logger log = LoggerFactory.getLogger(TestcontainersBase.class);

  /** PostgreSQL 18 Testcontainer */
  @Container
  @SuppressWarnings("resource")
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
          .withDatabaseName("ydsz_test")
          .withUsername("test")
          .withPassword("test");

  /** Redis 7 Testcontainer */
  @Container
  @SuppressWarnings("resource")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  /**
   * 注册 Testcontainers 连接属性，覆盖 Nacos 配置中心的 datasource/redis 设置。
   *
   * <p>Spring 在 ApplicationContext 加载前调用此方法，将动态生成的容器连接信息
   * 注入到 {@link DynamicPropertyRegistry}。由于 {@code @ActiveProfiles("test")} 配合
   * 子类的 {@code @TestPropertySource} 已禁用 Nacos 配置中心，此处提供的属性直接生效。
   *
   * @param registry 动态属性注册器
   */
  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> 5);
    registry.add("spring.datasource.hikari.connection-timeout", () -> 3000);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("ydsz.jdbc.dynamic-datasource.enabled", () -> false);

    log.info("Testcontainers initialized: PG url={}, Redis={}:{}",
        POSTGRES.getJdbcUrl(), REDIS.getHost(), REDIS.getMappedPort(6379));
  }
}
