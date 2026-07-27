package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_dict_item")
public class DictItem extends MpBaseEntity<String> {

    private String typeCode;
    private String itemCode;
    private String itemValue;
    private Integer sortOrder;
    private String parentId;
    private String description;
    private String extJson;

}
