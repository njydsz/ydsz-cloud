package com.njydsz.pmis.user.entity;

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

    @TableId(type = IdType.AUTO)
    private Long id;

    private String poolCode;        // 业务编号
    private String poolName;        // 池名称
    private String poolType;        // PoolType.code
    private Long departmentId;      // 事业部/部门 ID
    private String departmentName;
    private String levelRange;      // 职级范围 e.g. "L1-L3" "L4-L12" "L13+"
    private Integer headcount;      // 池人数
    private Integer billableTarget; // 目标计费人数
    private String description;
    private String status;          // ACTIVE/INACTIVE
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
