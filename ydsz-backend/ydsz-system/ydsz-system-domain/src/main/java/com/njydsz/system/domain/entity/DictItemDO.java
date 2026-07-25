package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 字典项 DO。
 *
 * <p>对应数据库表 {@code ydsz_dict_item}，字段与 DDL 完全对齐。
 * 注意：数据库表中列名为 {@code item_value}（非 {@code item_name}），
 * 且包含 {@code parent_id}（树形字典）和 {@code ext_json}（扩展属性 JSONB）。
 *
 * @author ydsz-team
 */
@Data
@TableName("ydsz_dict_item")
public class DictItemDO {
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
    private String itemCode;
    private String itemValue;
    private Integer sortOrder;
    private String parentId;
    private String description;
    private String extJson;
    private String status;
}
