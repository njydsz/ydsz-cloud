package com.njydsz.system.domain.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;

import com.njydsz.system.domain.dto.TenantPlanDTO;
import com.njydsz.system.domain.query.TenantPlanPageQuery;
import com.njydsz.system.domain.query.TenantPlanQuery;
import com.njydsz.system.domain.vo.TenantPlanVO;

/**
 * 租户方案仓储接口（domain 层契约）。
 *
 * <p>定义租户方案域的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link TenantPlanVO}），非 DTO / infra 实体
 *   <li>CUD 入参使用领域 DTO（{@link TenantPlanDTO}），禁止接受 infra 实体
 *   <li>查询入参使用领域 Query（{@link TenantPlanPageQuery} / {@link TenantPlanQuery}）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TenantPlanRepository {

  /**
   * 根据主键查询方案。
   *
   * @param id 方案主键
   * @return 方案 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<TenantPlanVO> findById(String id);

  /**
   * 分页查询方案。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  IPage<TenantPlanVO> findByPage(TenantPlanPageQuery query);

  /**
   * 按条件查询方案列表。
   *
   * @param query 查询条件
   * @return 方案 VO 列表
   */
  List<TenantPlanVO> findList(TenantPlanQuery query);

  /**
   * 按条件统计方案数量。
   *
   * @param query 查询条件
   * @return 方案数量
   */
  long countByCondition(TenantPlanQuery query);

  /**
   * 插入方案。
   *
   * @param dto 方案 DTO
   * @return 插入成功返回 {@code true}
   */
  boolean insert(TenantPlanDTO dto);

  /**
   * 更新方案。
   *
   * @param dto 方案 DTO
   * @return 更新成功返回 {@code true}
   */
  boolean updateById(TenantPlanDTO dto);

  /**
   * 逻辑删除方案。
   *
   * @param id 方案 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);
}
