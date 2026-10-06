package com.njydsz.common.testcontainers;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers 集成测试基类 — 提供 PostgreSQL + Redis 容器实例。
 *
 * <p>使用方式：
 * <pre>{@code
 * public class MyRepositoryTest extends TestcontainersBase {
 *   @Test
 *   void testWithRealDatabase() {
 *     String jdbcUrl = getPostgresJdbcUrl();
 *     // 使用 jdbcUrl / getPostgresUsername() / getPostgresPassword() 初始化数据源
 *   }
 * }
 * }</pre>
 *
 * <p><b>注意</b>：运行测试需要 Docker 环境。CI 中需要配置 Docker-in-Docker 或容器调度。
 *
 * @author ydsz-team
 * @since 26.10.05
 */
public abstract class TestcontainersBase {

  /** PostgreSQL 18 容器（共享静态实例，所有子类共用） */
  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
          .withDatabaseName("ydsz_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(true);

  /** Redis 7 容器 */
  protected static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
          .withExposedPorts(6379)
          .withReuse(true);

  static {
    POSTGRES.start();
    REDIS.start();
  }

  /**
   * 获取 PostgreSQL JDBC URL。
   *
   * @return 如 jdbc:postgresql://localhost:5432/ydsz_test
   */
  protected String getPostgresJdbcUrl() {
    return POSTGRES.getJdbcUrl();
  }

  /**
   * 获取 PostgreSQL 用户名。
   *
   * @return 数据库用户名
   */
  protected String getPostgresUsername() {
    return POSTGRES.getUsername();
  }

  /**
   * 获取 PostgreSQL 密码。
   *
   * @return 数据库密码
   */
  protected String getPostgresPassword() {
    return POSTGRES.getPassword();
  }

  /**
   * 获取 Redis 主机。
   *
   * @return Redis 主机地址
   */
  protected String getRedisHost() {
    return REDIS.getHost();
  }

  /**
   * 获取 Redis 端口。
   *
   * @return Redis 映射端口
   */
  protected int getRedisPort() {
    return REDIS.getMappedPort(6379);
  }
}
