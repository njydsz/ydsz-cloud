package com.njydsz.system.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.njydsz.system.domain.entity.Variable;

/**
 * 系统变量仓储接口（Infra 层契约）。
 *
 * <p>定义变量域的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域实体（{@link Variable}），非 DTO / VO
 *   <li>分页查询通过 {@link Page} + {@link IPage} 标准契约返回
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface VariableRepository {

  /**
   * 状态常量：启用。
   */
  String STATUS_ENABLED = "ENABLED";

  /**
   * 按变量键查询启用的变量。
   *
   * @param variableKey 变量键
   * @return 变量实体；不存在返回 {@code Optional.empty()}
   */
  Optional<Variable> findEnabledByKey(String variableKey);

  /**
   * 按变量键查询变量（不区分状态，用于版本快照 / 回滚定位）。
   *
   * @param variableKey 变量键
   * @return 变量实体；不存在返回 {@code Optional.empty()}
   */
  Optional<Variable> findByKeyIgnoreStatus(String variableKey);

  /**
   * 根据主键查询变量。
   *
   * @param id 变量主键
   * @return 变量实体；不存在返回 {@code Optional.empty()}
   */
  Optional<Variable> findById(String id);

  /**
   * 分页查询变量。
   *
   * @param page 分页参数
   * @param variableKey 变量键模糊匹配（可选）
   * @param status 状态精确匹配（可选）
   * @return 分页结果
   */
  IPage<Variable> findByPage(Page<Variable> page, String variableKey, String status);

  /**
   * 查询全部变量（不区分状态）。
   *
   * @return 全部变量列表
   */
  List<Variable> findAll();

  /**
   * 按条件查询变量列表。
   *
   * @param wrapper 查询条件
   * @return 变量列表
   */
  List<Variable> findList(QueryWrapper<Variable> wrapper);

  /**
   * 插入变量。
   *
   * @param entity 变量实体
   * @return 插入成功返回 {@code true}
   */
  boolean insert(Variable entity);

  /**
   * 更新变量。
   *
   * @param entity 变量实体
   * @return 更新成功返回 {@code true}
   */
  boolean updateById(Variable entity);

  /**
   * 逻辑删除变量。
   *
   * @param id 变量 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);

  /**
   * 按租户 ID 查询未删除变量（用于搜索索引全量重建）。
   *
   * @param tenantId 租户 ID（null 或空表示全量）
   * @return 未删除变量列表
   */
  List<Variable> findByTenantId(String tenantId);
}
