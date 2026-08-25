package com.njydsz.system.infra.entity;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;




/**
 * 租户主表实体
 *
 * <p>对应数据库表 {@code ydsz_tenant}，SaaS 多租户核心元数据。 租户是平台上的最小业务隔离单位，每个租户拥有独立的用户、角色、权限和数据隔离边界。
 *
 * <p><b>多租户隔离策略：</b>
 *
 * <ul>
 *   <li><b>SHARED_DB（默认）</b>：共享数据库，通过 {@code tenant_id} 字段在 WHERE 条件中过滤 （参见 {@link
 *       com.njydsz.common.core.context.RequestContext}）
 *   <li><b>ISOLATE_DB：</b>独立数据库，通过 {@link #datasourceKey} 字段路由到独立 DataSource， 适用于金融/医疗等高隔离要求的客户
 * </ul>
 *
 * <p><b>套餐与配额：</b>{@link #planId} 关联 {@link TenantPlanDO}，定义该租户的功能权限和资源配额 （如最大用户数、最大项目数、最大存储空间）。
 *
 * <p><b>生命周期：</b>租户创建 → 套餐订阅（{@code planId} + {@code expireAt}）→ 续费 / 到期降级 / 释放。
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_tenant_code}（{@code tenant_code}），普通索引 {@code idx_plan_id}（{@code
 * plan_id}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see TenantPlanDO 租户套餐
 * @see com.njydsz.common.core.context.RequestContext 请求上下文
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_tenant")
@SuppressWarnings("unchecked")
public class Tenant extends MpBaseEntity<String> {

  /** 租户编码（唯一业务标识，租户登录/调用使用） */
  private String tenantCode;

  /** 租户名称（展示用） */
  private String tenantName;

  /** 联系人姓名 */
  private String contactName;

  /** 联系电话（脱敏返回） */
  private String contactPhone;

  /** 联系邮箱（脱敏返回） */
  private String contactEmail;

  /** 关联套餐 ID（{@link TenantPlanDO#getId()}） */
  private String planId;

  /** 订阅到期时间（到期后租户被自动锁定/降级） */
  private LocalDateTime expireAt;

  /** 独立数据源标识（ISOLATE_DB 模式下使用，路由到独立 DataSource） */
  private String datasourceKey;

  /** 备注 */
  private String remark;

  /** 启用状态值（与 ydsz_tenant.status 列约定一致） */
  private static final String STATUS_ENABLED = "ENABLED";

  /**
   * 判断租户是否已到期。
   *
   * <p>到期时间早于当前时间视为已到期（配合 {@code TenantExpireScheduler} 自动停用）。
   *
   * @return true 已到期；{@code expireAt} 为空视为永不过期
   */
  public boolean isExpired() {
    return expireAt != null && expireAt.isBefore(LocalDateTime.now());
  }

  /**
   * 判断租户当前是否可用（状态为启用且未到期）。
   *
   * @return true 可用
   */
  public boolean isActive() {
    return STATUS_ENABLED.equals(getStatus()) && !isExpired();
  }
}
