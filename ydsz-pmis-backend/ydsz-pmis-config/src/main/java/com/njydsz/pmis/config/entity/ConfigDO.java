package com.njydsz.pmis.config.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 系统配置实体
 *
 * <p>配置分多组：basic / workflow / business / integration
 * 区分 public / private（前端可见性）
 * 支持热发布（更新后即时生效）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_config")
public class ConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置分组 */
    private String configGroup;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 默认值 */
    private String defaultValue;

    /** STRING/NUMBER/BOOLEAN/JSON */
    private String valueType = "STRING";

    private String description;

    /** 1=前端可见（public），0=私有 */
    private Integer isPublic = 0;

    private Integer sortOrder = 0;

    private String status = "ENABLED";
}
