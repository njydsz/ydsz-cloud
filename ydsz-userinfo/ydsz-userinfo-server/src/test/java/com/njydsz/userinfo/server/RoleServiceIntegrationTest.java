package com.njydsz.userinfo.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.njydsz.userinfo.domain.dto.RoleDTO;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.server.service.RoleService;

/**
 * {@link RoleService} 集成测试。
 *
 * <p>验证角色在真实 PostgreSQL 数据库上的 CRUD 操作：
 * 创建角色 → 按 ID 查询 → 断言数据正确持久化。
 *
 * <p><b>运行前提：</b>需 Docker 环境支持（Testcontainers 拉起 PostgreSQL 18 容器）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@DisplayName("RoleService 集成测试")
class RoleServiceIntegrationTest extends BaseIntegrationTest {

  /** 被测服务：角色业务逻辑 */
  @Autowired
  private RoleService roleService;

  /**
   * 验证：新建角色应成功持久化并能按主键检索到完整数据。
   *
   * <p>测试流程：构造 RoleDTO → 调用 create → 获得主键 → 调用 getById → 断言字段正确。
   */
  @Test
  @DisplayName("should_persist_and_retrieve_role_when_creating_new_role")
  void should_persist_and_retrieve_role_when_creating_new_role() {
    // Given
    RoleDTO dto = new RoleDTO();
    dto.setRoleCode("ROLE_INTEGRATION_TEST");
    dto.setRoleName("集成测试角色");
    dto.setDescription("集成测试专用角色，测试完毕后可清理");
    dto.setDataScope("ALL");
    dto.setStatus("ENABLED");
    dto.setIsBuiltIn(false);

    // When
    String createdId = roleService.create(dto);

    // Then
    assertThat(createdId).isNotNull();
    RoleVO retrieved = roleService.getById(createdId);
    assertThat(retrieved).isNotNull();
    assertThat(retrieved.getRoleCode()).isEqualTo("ROLE_INTEGRATION_TEST");
    assertThat(retrieved.getRoleName()).isEqualTo("集成测试角色");
    assertThat(retrieved.getStatus()).isEqualTo("ENABLED");
    assertThat(retrieved.getIsBuiltIn()).isFalse();
  }
}
