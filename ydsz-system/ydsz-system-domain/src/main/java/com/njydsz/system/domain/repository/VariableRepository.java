package com.njydsz.system.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;

import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.query.VariablePageQuery;
import com.njydsz.system.domain.vo.VariableVO;

/**
 * 系统变量仓储接口（domain 层契约）。
 *
 * <p>定义变量域的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link VariableVO}），非 DTO / infra 实体
 *   <li>CUD 入参使用领域 DTO（{@link VariableDTO}），禁止接受 infra 实体
 *   <li>分页查询入参使用领域 Query（{@link VariablePageQuery}）
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
   * @return 变量 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<VariableVO> findEnabledByKey(String variableKey);

  /**
   * 按变量键查询变量（不区分状态，用于版本快照 / 回滚定位）。
   *
   * @param variableKey 变量键
   * @return 变量 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<VariableVO> findByKeyIgnoreStatus(String variableKey);

  /**
   * 根据主键查询变量。
   *
   * @param id 变量主键
   * @return 变量 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<VariableVO> findById(String id);

  /**
   * 分页查询变量。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  PageResponse<List<VariableVO>> findByPage(VariablePageQuery query);

  /**
   * 查询全部变量（不区分状态）。
   *
   * @return 全部变量 VO 列表
   */
  List<VariableVO> findAll();

  /**
   * 插入变量。
   *
   * @param dto 变量 DTO
   * @return 插入成功返回 {@code true}
   */
  boolean insert(VariableDTO dto);

  /**
   * 更新变量。
   *
   * @param dto 变量 DTO
   * @return 更新成功返回 {@code true}
   */
  boolean updateById(VariableDTO dto);

  /**
   * 逻辑删除变量。
   *
   * @param id 变量 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);
}
