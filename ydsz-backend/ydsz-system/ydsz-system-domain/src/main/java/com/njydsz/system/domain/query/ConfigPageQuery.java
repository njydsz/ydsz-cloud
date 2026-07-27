package com.njydsz.system.domain.query;

import com.njydsz.common.domain.query.PageQuery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统配置分页查询参数。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "系统配置分页查询参数")
public class ConfigPageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    @Schema(description = "配置分组")
    private String configGroup;

    @Schema(description = "配置键（模糊匹配）")
    private String configKey;

    @Schema(description = "启用状态：ENABLED/DISABLED")
    private String status;
}
