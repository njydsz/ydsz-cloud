package com.njydsz.system.infra.mapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import com.njydsz.system.infra.entity.Tenant;



/**
 * 租户 Mapper
 *
 * <p>对应数据表 <code>ydsz_sys_tenant</code>。
 *
 * <p>租户是系统多租户隔离的最高层（每条业务数据都通过 {@code tenant_id} 关联），租户状态/计划/到期时间集中管理。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_tenant_code — 租户编码唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>{@code ydsz_sys_tenant} 为平台级表（P1-4），应加入 {@code ydsz.tenant.ignore-tables}
 * 配置使其绕过租户拦截器；平台级查询（{@code TenantServiceImpl}）依赖该配置或超级管理员上下文。
 * 原生 {@link #disableExpiredTenants} 通过 {@code @InterceptorIgnore(tenantLine = "true")} 显式豁免，
 * 不依赖部署配置即可保证全量扫描。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see Tenant 租户实体
 * @see com.njydsz.system.server.service.TenantService 租户 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {

  /**
   * 原子停用所有已到期租户（P1-3 租户到期自动锁定）。
   *
   * <p>租户调度任务调用：将 {@code status=ENABLED} 且 {@code expire_at} 已过期的租户批量置为
   * {@code DISABLED}。使用原生 UPDATE 保证原子性与全量扫描（租户管理是平台级视角，不注入 tenant_id 过滤）。
   *
   * <p><b>P1-4 修复：</b>显式声明 {@code @InterceptorIgnore(tenantLine = "true")} 绕过租户隔离拦截器，
   * 否则在无租户上下文的调度线程中会触发 fail-closed 的 {@code TenantIsolationException}，导致调度任务每次执行失败。
   *
   * @return 受影响的行数（被停用的租户数）
   */
  @InterceptorIgnore(tenantLine = "true")
  @Update(
      "UPDATE ydsz_sys_tenant SET status = 'DISABLED', updated_by = 'system', updated_at = NOW() "
          + "WHERE status = 'ENABLED' AND expire_at IS NOT NULL AND expire_at < NOW() "
          + "AND deleted = 0")
  int disableExpiredTenants();
}
