package com.njydsz.infra;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Infra 模块 Testcontainers 集成测试基类。
 *
 * <p>提供 PostgreSQL 18 真实数据库环境，集成测试直接对真实 DB 执行 CRUD，验证：
 *
 * <ul>
 *   <li>MyBatis-Plus Mapper 映射正确性
 *   <li>Repository 层业务逻辑
 *   <li>逻辑删除（is_deleted）过滤行为
 *   <li>多租户字段（tenant_id）持久化</li>
 * </ul>
 *
 * <p><b>使用前提：</b>运行环境需安装 Docker（Testcontainers 通过 Docker 拉取镜像并启动容器）。
 *
 * <p><b>数据隔离：</b>每个测试类运行完毕后应调用 {@link #truncateAllTables()} 清理数据；
 * 基类已通过 {@code @AfterEach} 自动清理字典表。
 *
 * <p><b>扩展方式：</b>其他模块可直接继承此类，或复制本模式到对应模块测试源目录下。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@SpringBootTest(classes = InfraTestApplication.class)
// disabledWithoutDocker：无 Docker 的开发机上以"可见跳过"结束，而不是报
// Could not find a valid Docker environment 红灯；CI 镜像内 Docker 可用时正常执行。
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
public abstract class AbstractContainerTestBase {

  /** PostgreSQL 18 测试容器（与生产环境保持一致） */
  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:18-alpine")
          .withDatabaseName("ydsz_test")
          .withUsername("test")
          .withPassword("test");

  /** JDBC 工具，用于清理测试数据 */
  @Autowired
  protected JdbcTemplate jdbcTemplate;

  /** 数据源引用（子类可获取连接做低级操作） */
  @Autowired
  protected DataSource dataSource;

  /**
   * 动态注册 Spring 属性：将 Testcontainers 的连接信息注入到 Spring Environment。
   *
   * <p>覆盖 application-test.yml 中的默认 datasource 配置，使用容器实际的动态端口。
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
  }

  /**
   * 每个测试方法执行后重置字典表数据，保证测试间相互隔离。
   *
   * <p>TRUNCATE ... RESTART IDENTITY CASCADE 清除所有数据并重置序列，
   * 级联清理依赖表数据。生产禁用，仅用于测试清理。
   */
  @AfterEach
  void cleanUpTables() {
    truncateAllTables();
  }

  /**
   * 清空所有业务测试表数据（TRUNCATE CASCADE）。
   *
   * <p>基类默认清理字典相关表。覆盖此方法以添加当前测试涉及的其他表。
   */
  protected void truncateAllTables() {
    // TODO: 添加其他业务表的清理语句，例如：
    // jdbcTemplate.execute("TRUNCATE TABLE ydsz_sys_config CASCADE");
    // jdbcTemplate.execute("TRUNCATE TABLE ydsz_sys_variable CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE ydsz_sys_dict_item CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE ydsz_sys_dict_type CASCADE");
  }

  /**
   * 从 classpath 加载 SQL 脚本并执行（用于测试前 schema 初始化）。
   *
   * <p><b>注意：</b>PostgreSQL 容器首次启动时表结构尚不存在，需通过 {@code @Sql} 注解
   * 或手动调用此方法初始化 schema。
   *
   * @param classpathLocation 脚本在 classpath 中的路径（如 "schema-test.sql"）
   */
  protected void executeSqlScript(String classpathLocation) {
    ResourceDatabasePopulator populator =
        new ResourceDatabasePopulator(new ClassPathResource(classpathLocation));
    populator.execute(dataSource);
  }
}
