package com.njydsz.userinfo.infra.repository;

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

import com.njydsz.userinfo.domain.repository.RoleRepository;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.infra._support.UserInfoInfraTestApplication;
import com.njydsz.userinfo.infra._support.UserInfoTestInitializer;

/**
 * RoleRepository 集成测试（Testcontainers PostgreSQL 16）。
 *
 * <p>验证 {@link RoleRepositoryImpl} 在真实 PG 环境下的查询行为：
 *
 * <ul>
 *   <li>{@code findById} — 按主键 ID 检索角色</li>
 *   <li>{@code findByRoleCode} — 按角色编码检索（RBAC 权限匹配入口）</li>
 *   <li>{@code findByIds} — 按 ID 集合批量检索</li>
 * </ul>
 *
 * <p><b>测试策略：</b>每条 IT 开始前通过 {@code @Sql} 加载 {@code /schema-role.sql} 初始化表结构，
 * 通过 {@link JdbcTemplate} 直接插入测试数据，仅测试 Infra 仓储层行为。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SpringBootTest(classes = UserInfoInfraTestApplication.class)
@Sql(scripts = "/schema-role.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RoleRepositoryIT extends UserInfoTestInitializer {

  @Autowired private RoleRepository roleRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  private static final String TEST_TENANT = "test-tenant-role";

  /**
   * 通过 JDBC 批量插入测试角色。
   */
  private void insertRole(String id, String roleCode, String roleName, String status,
                         Boolean isBuiltIn, Integer sort, String description) {
    jdbcTemplate.update(
        "INSERT INTO ydsz_rbac_role (id, tenant_id, role_code, role_name, description, sort, is_built_in, data_scope, status, is_deleted, revision, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id, TEST_TENANT, roleCode, roleName, description, sort,
        isBuiltIn != null && isBuiltIn ? 1 : 0, "ALL", status, 0, 0,
        LocalDateTime.now(), LocalDateTime.now());
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    @DisplayName("有效 ID 存在时应返回对应角色 VO")
    void shouldReturnRoleWhenIdExists() {
      insertRole("role-id-001", "ROLE_TEST_1", "测试角色A", "ENABLED", false, 0, "itest-a");

      Optional<RoleVO> result = roleRepository.findById("role-id-001");

      assertThat(result).isPresent();
      assertThat(result.get().getRoleCode()).isEqualTo("ROLE_TEST_1");
      assertThat(result.get().getRoleName()).isEqualTo("测试角色A");
    }

    @Test
    @DisplayName("ID 不存在时应返回 empty")
    void shouldReturnEmptyWhenIdNotExists() {
      Optional<RoleVO> result = roleRepository.findById("no-such-id");

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByRoleCode")
  class FindByRoleCode {

    @Test
    @DisplayName("角色编码存在时应返回对应角色 VO")
    void shouldReturnRoleWhenCodeExists() {
      insertRole("role-id-002", "ROLE_MANAGER", "部门经理", "ENABLED", false, 0, null);

      Optional<RoleVO> result = roleRepository.findByRoleCode("ROLE_MANAGER");

      assertThat(result).isPresent();
      assertThat(result.get().getRoleName()).isEqualTo("部门经理");
      assertThat(result.get().getStatus()).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("角色编码不存在时应返回 empty")
    void shouldReturnEmptyWhenCodeNotExists() {
      Optional<RoleVO> result = roleRepository.findByRoleCode("ROLE_GHOST");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("逻辑删除后应返回 empty")
    void shouldReturnEmptyWhenRoleLogicallyDeleted() {
      // 直接通过 JDBC 插入已逻辑删除的记录
      jdbcTemplate.update(
          "INSERT INTO ydsz_rbac_role (id, tenant_id, role_code, role_name, description, sort, is_built_in, data_scope, status, is_deleted, revision, created_at, updated_at) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
          "role-del-001", TEST_TENANT, "ROLE_DELETED", "should-be-deleted",
          null, 0, 0, "ALL", "ENABLED", 1, 0,
          LocalDateTime.now(), LocalDateTime.now());

      Optional<RoleVO> result = roleRepository.findByRoleCode("ROLE_DELETED");

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByIds")
  class FindByIds {

    @Test
    @DisplayName("应返回 ID 集合中对应的角色列表")
    void shouldReturnAllFoundRolesByIds() {
      insertRole("r-101", "ROLE_A", "角色 A", "ENABLED", false, 3, null);
      insertRole("r-102", "ROLE_B", "角色 B", "ENABLED", false, 2, null);
      insertRole("r-103", "ROLE_C", "角色 C", "DISABLED", false, 1, null);

      List<RoleVO> result = roleRepository.findByIds(java.util.List.of("r-101", "r-102", "r-103"));

      assertThat(result).hasSize(3);
      assertThat(result).extracting(RoleVO::getRoleCode)
          .containsExactlyInAnyOrder("ROLE_A", "ROLE_B", "ROLE_C");
    }

    @Test
    @DisplayName("传入空集合应返回空列表")
    void shouldReturnEmptyListForEmptyIds() {
      List<RoleVO> result = roleRepository.findByIds(java.util.List.of());

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("包含不存在 ID 时只返回已找到的")
    void shouldReturnOnlyFoundForPartialIds() {
      insertRole("r-201", "ROLE_EXISTS", "角色 E", "ENABLED", false, 0, null);

      List<RoleVO> result = roleRepository.findByIds(
          java.util.List.of("r-201", "non-exist-id", "non-exist-2"));

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getRoleCode()).isEqualTo("ROLE_EXISTS");
    }
  }
}
