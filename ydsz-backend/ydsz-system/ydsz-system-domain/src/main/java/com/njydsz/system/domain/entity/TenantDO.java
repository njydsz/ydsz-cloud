package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 租户主表实体
 *
 * <p>SaaS 多租户核心元数据管理
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_tenant")
public class TenantDO extends MpBaseEntity<String> {

    /** 租户编码（唯一业务标识） */
    private String tenantCode;

    /** 租户名称 */
    private String tenantName;

    /** 联系人姓名 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 关联套餐 ID */
    private String planId;

    /** 订阅到期时间 */
    private LocalDateTime expireAt;

    /** 独立数据源标识（ISOLATE_DB 模式下使用） */
    private String datasourceKey;

    /** 备注 */
    private String remark;

}
