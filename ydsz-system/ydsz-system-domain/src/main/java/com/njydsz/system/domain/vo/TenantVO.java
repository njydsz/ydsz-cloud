package com.njydsz.system.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 租户 VO
 *
 * <p>对应 {@code ydsz_tenant} 表的展示视图，是「租户管理」列表 / 详情接口的返回值类型。 由 {@link
 * com.njydsz.system.domain.converter.SystemConverter} 从 {@link
 * com.njydsz.system.domain.entity.Tenant} 实体转换而来。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code tenantCode} — 租户编码，全局唯一标识
 *   <li>{@code planId} — 关联套餐 ID
 *   <li>{@code expireAt} — 套餐到期时间，到期后自动降级为基础版
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.Tenant 租户实体
 * @see com.njydsz.system.domain.dto.TenantDTO 租户 DTO
 */
@Data
public class TenantVO {

  private String id;

  private String tenantCode;

  private String tenantName;

  private String contactName;

  private String contactPhone;

  private String contactEmail;

  private String planId;

  private LocalDateTime expireAt;

  private String datasourceKey;

  private String status;

  private String remark;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
