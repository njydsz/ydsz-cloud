package com.njydsz.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.njydsz.infra.AbstractContainerTestBase;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.repository.DictRepository;
import com.njydsz.system.domain.vo.DictTypeVO;

/**
 * {@link DictRepository} 集成测试。
 *
 * <p>在真实 PostgreSQL 18 容器上验证字典仓储的核心能力：插入 → 查询 → 逻辑删除。
 *
 * <p><b>覆盖范围：</b>
 *
 * <ul>
 *   <li>insertType：字典类型入库 + 雪花 ID 回填
 *   <li>findTypeById：按主键精确查询
 *   <li>deleteTypeById：@TableLogic 逻辑删除（is_deleted 自动过滤）
 *   <li>existsTypeCode：唯一性校验
 * </ul>
 *
 * <p><b>运行前提：</b>需 Docker 环境（Testcontainers 拉起 PostgreSQL 18 容器）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@DisplayName("集成测试 - DictRepository 字典仓储")
class DictRepositoryIT extends AbstractContainerTestBase {

  /** 被测仓储：字典类型 CRUD */
  @Autowired
  private DictRepository dictRepository;

  /** JDBC 工具：用于底层数据验证 */
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUpSchema() {
    // 确保 PostgreSQL 容器中存在 dict 表（首次运行拉取容器后表为空）
    // 使用 CREATE TABLE IF NOT EXISTS 幂等操作，重复执行无害
    executeSqlScript("schema-test.sql");
  }

  /**
   * 验证：PostgreSQL Testcontainers 容器正常运行。
   *
   * <p>基础健康检查：通过 JdbcTemplate 执行简单查询确认数据库连接可用。
   */
  @Test
  @DisplayName("集成测试 - Testcontainers PostgreSQL 容器运行正常")
  void should_postgres_container_be_running() {
    // When
    Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

    // Then
    assertThat(result).isEqualTo(1);
  }

  // TODO: 后续可补充以下场景的测试方法：
  // 1. findTypePage 分页查询（验证分页参数与排序）
  // 2. updateTypeById 更新操作（验证乐观锁 revision 递增）
  // 3. countItemsByTypeCode 统计字典项数量
  // 4. existsTypeCode 重复校验（含排除自身逻辑）
  // 5. findAllTypes 查询全部字典类型
  // 6. CRUD 字典项（insertItem / findItemById / deleteItemById）
  // 7. 多租户隔离验证（不同 tenant_id 的数据互相隔离）

  /**
   * 验证：插入字典类型应成功持久化并能按主键检索到完整数据。
   *
   * <p>测试流程：构造 DictTypeDTO → insertType → 获得主键 → findTypeById → 断言字段正确。
   */
  @Test
  @DisplayName("集成测试 - 插入字典类型后能按主键查询到完整数据")
  void should_persist_and_retrieve_dict_type_when_inserting() {
    // Given
    DictTypeDTO dto = new DictTypeDTO();
    dto.setTypeCode("IT_STATUS");
    dto.setTypeName("集成测试状态");
    dto.setDescription("集成测试专用字典类型，测试完毕后可清理");
    dto.setStatus("ENABLED");

    // When
    boolean inserted = dictRepository.insertType(dto);

    // Then
    assertThat(inserted).isTrue();
    assertThat(dto.getId()).isNotNull();
    Optional<DictTypeVO> result = dictRepository.findTypeById(dto.getId());
    assertThat(result).isPresent();
    DictTypeVO vo = result.get();
    assertThat(vo.getTypeCode()).isEqualTo("IT_STATUS");
    assertThat(vo.getTypeName()).isEqualTo("集成测试状态");
    assertThat(vo.getStatus()).isEqualTo("ENABLED");
  }

  /**
   * 验证：逻辑删除字典类型后，findTypeById 应返回 Optional.empty()。
   *
   * <p>测试 MyBatis-Plus @TableLogic 注解的核心行为：deleteById 转为 UPDATE SET is_deleted=1，
   * 后续查询自动追加 AND is_deleted=0 条件。
   */
  @Test
  @DisplayName("集成测试 - 逻辑删除后字典类型不可再被查询到")
  void should_not_find_logically_deleted_dict_type() {
    // Given
    DictTypeDTO dto = new DictTypeDTO();
    dto.setTypeCode("IT_DELETABLE");
    dto.setTypeName("可删除测试字典");
    dto.setStatus("ENABLED");
    dictRepository.insertType(dto);
    assertThat(dto.getId()).isNotNull();

    // When
    boolean deleted = dictRepository.deleteTypeById(dto.getId());

    // Then
    assertThat(deleted).isTrue();
    Optional<DictTypeVO> result = dictRepository.findTypeById(dto.getId());
    assertThat(result).isEmpty();

    // 底层验证：数据库中记录仍存在但 is_deleted 标记为 1（逻辑删除而非物理删除）
    Integer deletedFlag =
        jdbcTemplate.queryForObject(
            "SELECT is_deleted FROM ydsz_sys_dict_type WHERE id = ?",
            Integer.class,
            dto.getId());
    assertThat(deletedFlag).isEqualTo(1);
  }

  /**
   * 验证：existsTypeCode 应正确检测编码唯一性。
   *
   * <p>测试场景：插入 typeCode=GENDER 后，existsTypeCode("GENDER") 返回 true；
   * existsTypeCode("UNKNOWN") 返回 false。
   */
  @Test
  @DisplayName("集成测试 - 能正确检测字典类型编码的唯一性")
  void should_detect_duplicate_type_code() {
    // Given
    DictTypeDTO dto = new DictTypeDTO();
    dto.setTypeCode("IT_GENDER");
    dto.setTypeName("集成测试性别");
    dto.setStatus("ENABLED");
    dictRepository.insertType(dto);

    // When + Then
    assertThat(dictRepository.existsTypeCode("IT_GENDER", null)).isTrue();
    assertThat(dictRepository.existsTypeCode("IT_UNKNOWN", null)).isFalse();
  }

  /**
   * 验证：多租户字段 tenant_id 正确持久化。
   *
   * <p>测试流程：插入字典类型 → 验证数据库中 tenant_id 为默认值 '0'（SaaS 单租户模式），
   * 查询返回到 VO 对象中。
   */
  @Test
  @DisplayName("集成测试 - 多租户字段 tenant_id 正确持久化")
  void should_persist_tenant_id_field() {
    // Given
    DictTypeDTO dto = new DictTypeDTO();
    dto.setTypeCode("IT_TENANT");
    dto.setTypeName("租户测试字典");
    dto.setStatus("ENABLED");

    // When
    dictRepository.insertType(dto);
    assertThat(dto.getId()).isNotNull();

    // Then: 验证数据库中 tenant_id 的值
    // MpBaseEntity 默认值 tenant_id='0'（单租户模式），insert 时由字段自动填充
    String tenantId =
        jdbcTemplate.queryForObject(
            "SELECT tenant_id FROM ydsz_sys_dict_type WHERE id = ?",
            String.class,
            dto.getId());
    assertThat(tenantId).isEqualTo("0");

    // 验证查询返回的 VO 中包含正确数据
    Optional<DictTypeVO> result = dictRepository.findTypeById(dto.getId());
    assertThat(result).isPresent();
    assertThat(result.get().getTypeCode()).isEqualTo("IT_TENANT");
  }

  /**
   * 验证：findAllTypes 返回当前租户下所有未删除的字典类型。
   */
  @Test
  @DisplayName("集成测试 - 查询全部未删除的字典类型")
  void should_return_all_undeleted_types() {
    // Given
    for (int i = 1; i <= 3; i++) {
      DictTypeDTO dto = new DictTypeDTO();
      dto.setTypeCode("IT_ALL_" + i);
      dto.setTypeName("批量查询测试_" + i);
      dto.setStatus("ENABLED");
      dictRepository.insertType(dto);
    }

    // When
    List<DictTypeVO> allTypes = dictRepository.findAllTypes();

    // Then: 至少能查到本次测试插入的 3 条记录（不包括被逻辑删除的）
    List<String> itCodes =
        allTypes.stream()
            .map(DictTypeVO::getTypeCode)
            .filter(code -> code.startsWith("IT_ALL_"))
            .toList();
    assertThat(itCodes).hasSize(3);
  }
}
