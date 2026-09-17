package com.njydsz.system.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import com.njydsz.system.domain.repository.ConfigRepository;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.infra._support.SystemInfraTestApplication;
import com.njydsz.system.infra._support.SystemTestInitializer;

/**
 * ConfigRepository 集成测试（Testcontainers PostgreSQL 16）。
 *
 * <p>验证 {@link ConfigRepositoryImpl} 在真实 PG 环境下的查询行为：
 *
 * <ul>
 *   <li>{@code findEnabledByKey} — 按 key 检索启用状态的配置</li>
 *   <li>{@code findByKeyIgnoreStatus} — 忽略状态按 key 检索</li>
 *   <li>{@code findEnabledByGroup} — 按分组查询启用配置列表并排序</li>
 * </ul>
 *
 * <p><b>测试策略：</b>每条 IT 开始前通过 {@code @Sql} 加载 {@code /schema-config.sql} 初始化表结构，
 * 通过 {@link JdbcTemplate} 直接插入测试数据（避免 Service 层依赖），只测试 Infra 仓储层行为。
 *
 * <p><b>数据隔离：</b>schema 启用 {@code DROP TABLE ... CASCADE} 每次重建，
 * {@code PER_CLASS} 实例下所有用例在同一个 Testcontainers 容器中运行，通过表重建保证隔离。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SpringBootTest(classes = SystemInfraTestApplication.class)
@Sql(scripts = "/schema-config.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ConfigRepositoryIT extends SystemTestInitializer {

  @Autowired private ConfigRepository configRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  private static final String TEST_TENANT = "test-tenant-cfg";

  /**
   * 通过 JDBC 批量插入测试配置（绕过 Service 层，避免业务 Service 未在当前上下文中加载的问题）。
   * 仅填充仓储查询所需字段。
   */
  private void insertConfig(String group, String key, String value, String valueType,
                           String status, boolean isDeleted, boolean isPublic, int sort) {
    jdbcTemplate.update(
        "INSERT INTO ydsz_sys_config (id, tenant_id, config_group, config_key, config_value, value_type, status, is_deleted, is_public, sort, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        key + "-" + group, TEST_TENANT, group, key, value, valueType,
        status, isDeleted ? 1 : 0, isPublic ? 1 : 0, sort,
        LocalDateTime.now(), LocalDateTime.now());
  }

  @Nested
  @DisplayName("findEnabledByKey")
  class FindEnabledByKey {

    @Test
    @DisplayName("应按 key 检索启用状态且未删除的配置 — 返回 Optional 包含 VO")
    void shouldReturnEnabledConfigByKey() {
      insertConfig("system", "app.name", "YDSZ测试", "STRING", "ENABLED", false, false, 1);

      Optional<ConfigVO> result = configRepository.findEnabledByKey("app.name");

      assertThat(result).isPresent();
      assertThat(result.get().getConfigValue()).isEqualTo("YDSZ测试");
      assertThat(result.get().getConfigGroup()).isEqualTo("system");
    }

    @Test
    @DisplayName("配置处于 DISABLED 状态时应返回 empty")
    void shouldReturnEmptyWhenDisabled() {
      insertConfig("system", "feature.x", "true", "BOOLEAN", "DISABLED", false, false, 1);

      Optional<ConfigVO> result = configRepository.findEnabledByKey("feature.x");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("配置被逻辑删除时应返回 empty")
    void shouldReturnEmptyWhenLogicallyDeleted() {
      insertConfig("system", "deprecated.key", "old", "STRING", "ENABLED", true, false, 1);

      Optional<ConfigVO> result = configRepository.findEnabledByKey("deprecated.key");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("key 不存在时应返回 empty")
    void shouldReturnEmptyWhenKeyDoesNotExist() {
      Optional<ConfigVO> result = configRepository.findEnabledByKey("non.exist.key");

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByKeyIgnoreStatus")
  class FindByKeyIgnoreStatus {

    @Test
    @DisplayName("应忽略状态返回配置 — DISABLED 状态也返回")
    void shouldReturnConfigIgnoringStatus() {
      insertConfig("cache", "ttl.seconds", "300", "NUMBER", "DISABLED", false, false, 1);

      Optional<ConfigVO> result = configRepository.findByKeyIgnoreStatus("ttl.seconds");

      assertThat(result).isPresent();
      assertThat(result.get().getConfigValue()).isEqualTo("300");
    }

    @Test
    @DisplayName("逻辑删除后即使忽略状态也返回 empty")
    void shouldReturnEmptyWhenDeletedEvenIgnoringStatus() {
      insertConfig("cache", "expired", "1", "NUMBER", "ENABLED", true, false, 1);

      Optional<ConfigVO> result = configRepository.findByKeyIgnoreStatus("expired");

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findEnabledByGroup")
  class FindEnabledByGroup {

    @Test
    @DisplayName("应按分组返回启用配置列表并按 sort 升序排列")
    void shouldReturnEnabledConfigsOrderedBySort() {
      insertConfig("feature", "switch.a", "1", "BOOLEAN", "ENABLED", false, true, 3);
      insertConfig("feature", "switch.b", "2", "BOOLEAN", "ENABLED", false, true, 1);
      insertConfig("feature", "switch.c", "3", "BOOLEAN", "ENABLED", false, true, 2);
      // 不应出现：DISABLED
      insertConfig("feature", "switch.d", "4", "BOOLEAN", "DISABLED", false, true, 0);

      List<ConfigVO> result = configRepository.findEnabledByGroup("feature");

      assertThat(result).hasSize(3);
      // 按 sort 升序：b(1) → c(2) → a(3)
      assertThat(result.get(0).getConfigKey()).isEqualTo("switch.b");
      assertThat(result.get(1).getConfigKey()).isEqualTo("switch.c");
      assertThat(result.get(2).getConfigKey()).isEqualTo("switch.a");
    }

    @Test
    @DisplayName("分组下无启用配置时应返回空列表")
    void shouldReturnEmptyWhenNoEnabledInGroup() {
      insertConfig("empty.grp", "x", "1", "STRING", "DISABLED", false, false, 1);

      List<ConfigVO> result = configRepository.findEnabledByGroup("empty.grp");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("全部分组逻辑删除后应返回空列表")
    void shouldReturnEmptyWhenAllLogicallyDeleted() {
      insertConfig("deleted.grp", "x", "1", "STRING", "ENABLED", true, false, 1);

      List<ConfigVO> result = configRepository.findEnabledByGroup("deleted.grp");

      assertThat(result).isEmpty();
    }
  }
}
