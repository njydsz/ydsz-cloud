package com.njydsz.system.domain.vo;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 租户 VO
 *
 * <p>对应 {@code ydsz_tenant} 表的展示视图，是「租户管理」列表 / 详情接口的返回值类型。
 * 由 {@link com.njydsz.system.domain.converter.SystemConverter} 从
 * {@link com.njydsz.system.domain.entity.Tenant} 实体转换而来。
 *
 * <p><b>字段语义：</b>
 * <ul>
 *   <li>{@code tenantCode} — 租户编码，全局唯一标识</li>
 *   <li>{@code planId} — 关联套餐 ID</li>
 *   <li>{@code expireAt} — 套餐到期时间，到期后自动降级为基础版</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.Tenant 租户实体
 * @see com.njydsz.system.domain.dto.TenantDTO 租户 DTO
 */
@Data
@Schema(description = "租户视图对象")
public class TenantVO {

    @Schema(description = "主键 ID")
    private String id;

    @Schema(description = "租户编码")
    private String tenantCode;

    @Schema(description = "租户名称")
    private String tenantName;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "联系邮箱")
    private String contactEmail;

    @Schema(description = "关联套餐 ID")
    private String planId;

    @Schema(description = "订阅到期时间")
    private LocalDateTime expireAt;

    @Schema(description = "独立数据源标识")
    private String datasourceKey;

    @Schema(description = "状态: ENABLED/DISABLED/EXPIRED")
    private String status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
