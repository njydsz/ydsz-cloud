package com.njydsz.pmis.userinfo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资源池
 *
 * <p>按 PoolType（HQ/DIVISION/RESERVE）三级管理资源。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_resource_pool")
public class ResourcePoolDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号 */
    private String poolCode;
    /** 池名称 */
    private String poolName;
    /** 池类型（PoolType.code） */
    private String poolType;
    /** 事业部/部门 ID */
    private Long departmentId;
    /** 部门名称 */
    private String departmentName;
    /** 职级范围 e.g. "L1-L3" "L4-L12" "L13+" */
    private String levelRange;
    /** 池人数 */
    private Integer headcount;
    /** 目标计费人数 */
    private Integer billableTarget;
    /** 描述 */
    private String description;
    /** 状态：ACTIVE/INACTIVE */
    private String status;
    /** 租户 ID */
    private Long tenantId;
    /** 外部提供方链路追踪 ID */
    private String providerTraceId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标识：0=未删除，1=已删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
