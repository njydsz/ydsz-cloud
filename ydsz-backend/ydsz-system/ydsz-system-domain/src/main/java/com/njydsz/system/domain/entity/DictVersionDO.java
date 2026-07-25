package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典版本 DO。
 *
 * <p>对应数据库表 {@code ydsz_dict_version}，记录字典变更历史快照，支持回滚与变更审计。
 * 字段与 DDL 完全对齐（含 tenant_id/updated_by/updated_at）。
 *
 * @author ydsz-team
 */
@Data
@TableName("ydsz_dict_version")
public class DictVersionDO {
    @TableId
    private String id;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private String tenantId;

    private String typeCode;
    private String version;
    private String changeLog;
    private LocalDateTime effectiveDate;
}
