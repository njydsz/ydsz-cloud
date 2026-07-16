package com.njydsz.system.domain.entity.config;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.domain.entity.BaseDO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统配置实体
 *
 * <p>配置分多组：basic / workflow / business / integration
 * 区分 public / private（前端可见性）
 * 支持热发布（更新后即时生效）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_config")
public class ConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

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

    /** 配置描述 */
    private String description;

    /** 1=前端可见（public），0=私有 */
    private Integer isPublic = 0;

    /** 排序序号 */
    private Integer sortOrder = 0;

    /** 状态：ENABLED/DISABLED */
    private String status = "ENABLED";
}
