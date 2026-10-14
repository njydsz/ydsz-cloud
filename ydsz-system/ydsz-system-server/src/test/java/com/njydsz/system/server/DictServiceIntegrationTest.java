package com.njydsz.system.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.server.service.DictService;

/**
 * {@link DictService} 集成测试。
 *
 * <p>验证字典类型在真实 PostgreSQL 数据库上的 CRUD 操作：
 * 保存字典类型 → 按 ID 查询 → 断言数据正确持久化。
 *
 * <p><b>运行前提：</b>需 Docker 环境支持（Testcontainers 拉起 PostgreSQL 18 容器）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@DisplayName("DictService 集成测试")
class DictServiceIntegrationTest extends BaseIntegrationTest {

  /** 被测服务：字典类型业务逻辑 */
  @Autowired
  private DictService dictService;

  /**
   * 验证：新建字典类型应成功持久化并能按主键检索到完整数据。
   *
   * <p>测试流程：构造 DictTypeDTO → 调用 save → 获得主键 → 调用 getById → 断言字段正确。
   */
  @Test
  @DisplayName("should_persist_and_retrieve_dictType_when_saving_new_dict")
  void should_persist_and_retrieve_dictType_when_saving_new_dict() {
    // Given
    DictTypeDTO dto = new DictTypeDTO();
    dto.setTypeCode("INTEGRATION_TEST_STATUS");
    dto.setTypeName("集成测试状态");
    dto.setDescription("集成测试专用字典类型，测试完毕后可清理");
    dto.setStatus("ENABLED");

    // When
    String savedId = dictService.save(dto);

    // Then
    assertThat(savedId).isNotNull();
    DictTypeVO retrieved = dictService.getById(savedId);
    assertThat(retrieved).isNotNull();
    assertThat(retrieved.getTypeCode()).isEqualTo("INTEGRATION_TEST_STATUS");
    assertThat(retrieved.getTypeName()).isEqualTo("集成测试状态");
    assertThat(retrieved.getStatus()).isEqualTo("ENABLED");
  }
}
