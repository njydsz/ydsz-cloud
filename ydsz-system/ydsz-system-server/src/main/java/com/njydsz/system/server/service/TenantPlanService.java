package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.vo.TenantPlanVO;

/**
 * 租户套餐 Service 接口
 *
 * <p>提供套餐（{@code ydsz_tenant_plan}）的 CRUD、分页查询等能力。 套餐定义租户的功能 / 容量 / 价格，是 SaaS 多租户定价模型的核心。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #save} / {@link #updateById} / {@link #removeById}
 *   <li><b>分页查询</b>：{@link #page} — 管理后台「套餐管理」列表
 *   <li><b>全部套餐</b>：{@link #listAll} — 租户注册页「选择套餐」下拉数据源
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.TenantPlan 套餐实体
 */
public interface TenantPlanService {

  /**
   * 按 ID 查询套餐
   *
   * @param id 主键 ID
   * @return 套餐 VO；不存在返回 {@code null}
   */
  TenantPlanVO getById(String id);

  /**
   * 分页查询套餐列表
   *
   * @param pageNum 当前页码
   * @param pageSize 每页记录数
   * @param planName 套餐名称模糊搜索（可选）
   * @param status 状态过滤（可选）
   * @return 分页结果
   */
  PageResponse<List<TenantPlanVO>> page(int pageNum, int pageSize, String planName, String status);

  /**
   * 查询全部启用套餐
   *
   * @return 套餐列表
   */
  List<TenantPlanVO> listAll();

  /**
   * 创建套餐
   *
   * @param dto 套餐 DTO
   * @return 新建套餐主键 ID
   */
  String save(TenantPlanVO vo);

  /**
   * 更新套餐
   *
   * @param dto 套餐 DTO（{@code id} 必填）
   * @return 是否成功
   */
  boolean updateById(TenantPlanVO vo);

  /**
   * 删除套餐
   *
   * <p>删除前需校验是否有关联租户，有关联时禁止删除。
   *
   * @param id 主键 ID
   * @return 是否成功
   */
  boolean removeById(String id);
}
