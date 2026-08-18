package com.njydsz.system.domain.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.njydsz.system.infra.entity.TenantPlan;

/**
 * 租户方案仓储接口（Infra 层契约）。
 *
 * <p>定义租户方案域的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域实体（{@link TenantPlan}），非 DTO / VO
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
   * @return 方案实体；不存在返回 {@code Optional.empty()}
   */
  Optional<TenantPlan> findById(String id);

  /**
   * 分页查询方案。
   *
   * @param page 分页参数
   * @param planName 方案名称模糊匹配（可选）
   * @param status 状态精确匹配（可选）
   * @return 分页结果
   */
  IPage<TenantPlan> findByPage(Page<TenantPlan> page, String planName, String status);

  /**
   * 按条件查询方案列表。
   *
   * @param wrapper 查询条件
   * @return 方案列表
   */
  List<TenantPlan> findList(LambdaQueryWrapper<TenantPlan> wrapper);

  /**
   * 按条件统计方案数量。
   *
   * @param wrapper 查询条件
   * @return 方案数量
   */
  long countByCondition(LambdaQueryWrapper<TenantPlan> wrapper);

  /**
   * 插入方案。
   *
   * @param entity 方案实体
   * @return 插入成功返回 {@code true}
   */
  boolean insert(TenantPlan entity);

  /**
   * 更新方案。
   *
   * @param entity 方案实体
   * @return 更新成功返回 {@code true}
   */
  boolean updateById(TenantPlan entity);

  /**
   * 逻辑删除方案。
   *
   * @param id 方案 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);
}
