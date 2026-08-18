package com.njydsz.system.domain.repository;

import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.njydsz.system.domain.dto.TenantDTO;
import com.njydsz.system.domain.vo.TenantVO;

/**
 * 租户仓储接口（domain 层契约）。
 *
 * <p>定义租户域的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link TenantVO}），非 DTO / infra 实体
 *   <li>CUD 入参使用领域 DTO（{@link TenantDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TenantRepository {

  /**
   * 根据主键查询租户。
   *
   * @param id 租户主键
   * @return 租户 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<TenantVO> findById(String id);

  /**
   * 分页查询租户。
   *
   * @param page 分页参数
   * @param tenantName 租户名称模糊匹配（可选）
   * @param status 状态精确匹配（可选）
   * @return 分页结果（VO 分页）
   */
  IPage<TenantVO> findByPage(Page<TenantVO> page, String tenantName, String status);

  /**
   * 按条件统计租户数量。
   *
   * @param wrapper 查询条件（基于 VO 字段构造）
   * @return 租户数量
   */
  long countByCondition(LambdaQueryWrapper<TenantVO> wrapper);

  /**
   * 插入租户。
   *
   * @param dto 租户 DTO
   * @return 插入成功返回 {@code true}
   */
  boolean insert(TenantDTO dto);

  /**
   * 更新租户。
   *
   * @param dto 租户 DTO
   * @return 更新成功返回 {@code true}
   */
  boolean updateById(TenantDTO dto);

  /**
   * 逻辑删除租户。
   *
   * @param id 租户 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);

  /**
   * 原子停用所有已到期租户。
   *
   * @return 受影响的行数（被停用的租户数）
   */
  int disableExpiredTenants();
}
