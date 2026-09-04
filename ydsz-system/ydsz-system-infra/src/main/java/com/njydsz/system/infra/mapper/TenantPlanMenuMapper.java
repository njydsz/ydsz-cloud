package com.njydsz.system.infra.mapper;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.system.domain.entity.TenantPlanMenu;




/**
 * 租户套餐-菜单关联 Mapper
 *
 * <p>对应数据表 <code>ydsz_sys_tenant_plan_menu</code>。
 *
 * <p>租户购买套餐后自动获得关联菜单的访问权限，是 RBAC 的「套餐级」权限分配。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_plan_menu — (套餐+菜单) 唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see TenantPlanMenu 套餐-菜单关联实体
 * @see com.njydsz.system.server.service.TenantPlanService 套餐 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface TenantPlanMenuMapper extends BaseMapper<TenantPlanMenu> {

  /**
   * 批量插入套餐-菜单关联（一次 SQL 批量写入）。
   *
   * <p>用于 {@code TenantPlanMenuServiceImpl.updatePlanMenus}，将 N 次单条 INSERT 合并为 1 次批量
   * INSERT，消除逐条写入的 N 次 DB 往返。审计字段（{@code id}/{@code createdAt}/{@code createdBy}/{@code
   * tenantId}）需由调用方预先填充（批量 XML 不走 MyBatis-Plus 拦截器）。
   *
   * @param items 关联实体列表
   * @return 插入的记录数
   */
  int insertBatch(@Param("items") List<TenantPlanMenu> items);
}
