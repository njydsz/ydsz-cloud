package com.njydsz.pmis.config.dto;

import com.njydsz.pmis.common.entity.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 配置分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "配置查询条件")
public class ConfigQueryDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关键字: 模糊匹配 configKey / configValue / description */
    private String keyword;

    /** 配置分组 */
    private String configGroup;
    /** 状态：ENABLED/DISABLED */
    private String status;
    /** 是否公开：1 公开 / 0 私有 */
    private Integer isPublic;
}
