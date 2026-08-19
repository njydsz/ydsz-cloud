package com.njydsz.system.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.EntityVersionCreateDTO;
import com.njydsz.system.domain.query.EntityVersionPageQuery;
import com.njydsz.system.domain.vo.EntityVersionVO;

/**
 * 统一实体版本仓储接口（domain 层契约）。
 *
 * <p>定义 Config/Dict/Variable 统一的版本数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link EntityVersionVO}），非 DTO / infra 实体
 *   <li>CUD 入参使用领域 DTO（{@link EntityVersionCreateDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface EntityVersionRepository {

  /**
   * 按资源类型 + 资源键查询版本历史（按生效时间倒序）。
   *
   * @param resourceType 资源类型（CONFIG/DICT/VARIABLE）
   * @param resourceKey 资源唯一标识
   * @return 版本 VO 列表（按生效时间倒序），无版本时返回空列表
   */
  List<EntityVersionVO> findByTypeAndKey(String resourceType, String resourceKey);

  /**
   * 按资源类型 + 资源键分页查询版本历史（P2-3 分页优化）。
   *
   * <p>支持翻页查询，避免一次性加载全部版本导致性能问题（版本量大的场景）。
   *
   * @param query 分页查询条件（resourceType / resourceKey / pageNum / pageSize）
   * @return 分页结果（含总记录数）
   */
  PageResponse<List<EntityVersionVO>> findPageByTypeAndKey(EntityVersionPageQuery query);

  /**
   * 按资源类型 + 资源键 + 版本号查询唯一版本。
   *
   * @param resourceType 资源类型
   * @param resourceKey 资源唯一标识
   * @param version 版本号
   * @return 版本 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<EntityVersionVO> findByTypeAndKeyAndVersion(
      String resourceType, String resourceKey, String version);

  /**
   * 保存版本记录。
   *
   * @param dto 版本创建 DTO
   * @return 保存后的版本 VO（含生成的主键 ID）
   */
  EntityVersionVO save(EntityVersionCreateDTO dto);
}
