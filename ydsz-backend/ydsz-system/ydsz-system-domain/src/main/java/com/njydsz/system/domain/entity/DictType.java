package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 字典类型 DO
 *
 * <p>对应数据库表 {@code ydsz_dict_type}，存储数据字典分类信息，
 * 如性别、状态、级别等枚举类型，每个字典类型下包含多个字典项（DictItem）。
 *
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_dict_type")
public class DictType extends MpBaseEntity<String> {

    /** 类型编码（唯一标识，用于业务引用） */
    private String typeCode;
    /** 类型名称（展示用） */
    private String typeName;
    /** 类型描述 */
    private String description;

}
