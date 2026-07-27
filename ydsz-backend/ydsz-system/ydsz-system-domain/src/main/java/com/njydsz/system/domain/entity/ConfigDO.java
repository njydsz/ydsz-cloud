package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统配置 DO
 *
 * <p>对应数据库表 {@code ydsz_config}，存储系统级配置项，
 * 支持按分组分类管理、按配置键查找，提供公开/私有配置区分，
 * 支持字符串、数字、布尔、JSON 等多种值类型。
 *
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_config")
public class ConfigDO extends MpBaseEntity<String> {

    /** 配置分组（用于按业务域分类管理配置） */
    private String configGroup;
    /** 配置键（同组内唯一标识） */
    private String configKey;
    /** 配置值 */
    private String configValue;
    /** 值类型（STRING/NUMBER/BOOLEAN/JSON，参见 ConfigValueType 枚举） */
    private String valueType;
    /** 默认值（配置未设置时使用） */
    private String defaultValue;
    /** 配置描述 */
    private String description;
    /** 是否公开配置（1=公开，前端可查；0=私有，仅后端可查） */
    private Integer isPublic;
    /** 排序序号 */
    private Integer sortOrder;

}
