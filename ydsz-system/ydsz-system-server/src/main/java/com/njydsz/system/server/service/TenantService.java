package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.TenantDTO;
import com.njydsz.system.domain.vo.TenantVO;

/**
 * 租户 Service 接口
 *
 * <p>提供租户（{@code ydsz_tenant}）的 CRUD、分页查询等能力。 租户是系统多租户隔离的最高层，支持套餐绑定、配额管理、到期控制。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #save} / {@link #updateById} / {@link #removeById}
 *   <li><b>分页查询</b>：{@link #page} — 管理后台「租户管理」列表
 *   <li><b>唯一性校验</b>：{@link #existsByTenantCode} — 创建 / 更新前检查租户编码唯一性
 * </ul>
 *
 * <p><b>多租户：</b>租户管理属于系统级超级管理员权限， 查询时不注入租户过滤条件，可跨租户查看全部租户。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.infra.entity.Tenant 租户实体
 */
public interface TenantService {

  /**
   * 按 ID 查询租户
   *
   * @param id 主键 ID
   * @return 租户 VO；不存在返回 {@code null}
   */
  TenantVO getById(String id);

  /**
   * 分页查询租户列表
   *
   * <p>支持按租户名称模糊匹配、状态过滤。
   *
   * @param pageNum 当前页码
   * @param pageSize 每页记录数
   * @param tenantName 租户名称模糊搜索（可选）
   * @param status 状态过滤（可选）
   * @return 分页结果
   */
  PageResponse<List<TenantVO>> page(int pageNum, int pageSize, String tenantName, String status);

  /**
   * 创建租户
   *
   * <p>写入前校验 {@code tenantCode} 全局唯一性。
   *
   * @param dto 租户 DTO
   * @return 新建租户主键 ID
   */
  String save(TenantDTO dto);

  /**
   * 更新租户
   *
   * <p>更新时校验 {@code tenantCode} 唯一性（排除自身）。
   *
   * @param dto 租户 DTO（{@code id} 必填）
   * @return 是否成功
   */
  boolean updateById(TenantDTO dto);

  /**
   * 删除租户（逻辑删除）
   *
   * <p>基于 MyBatis-Plus 逻辑删除（{@code @TableLogic}），不物理删除。
   *
   * @param id 主键 ID
   * @return 是否成功
   */
  boolean removeById(String id);

  /**
   * 检查租户编码是否已存在
   *
   * @param tenantCode 租户编码
   * @return 已存在返回 {@code true}，否则返回 {@code false}
   */
  boolean existsByTenantCode(String tenantCode);
}
