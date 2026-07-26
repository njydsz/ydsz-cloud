package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
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
@TableName("ydsz_tenant")
public class TenantDO {

    /** 主键 ID */
    @TableId
    private String id;

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

    /** 租户状态: ACTIVE 正常 / INACTIVE 未激活 / SUSPENDED 已停用 */
    private String status;

    /** 关联套餐 ID */
    private String planId;

    /** 订阅到期时间 */
    private LocalDateTime expireAt;

    /** 独立数据源标识（ISOLATE_DB 模式下使用） */
    private String datasourceKey;

    /** 备注 */
    private String remark;

    /** 创建人 ID */
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最后修改人 ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 最后修改时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记: 0 未删除 / 1 已删除 */
    @TableLogic
    private Integer deleted;
}
