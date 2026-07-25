package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统配置 VO。
 *
 * @author ydsz-team
 */
@Data
@Schema(description = "系统配置视图对象")
public class ConfigVO {

    @Schema(description = "主键 ID")
    private String id;

    @Schema(description = "配置分组")
    private String configGroup;

    @Schema(description = "配置键")
    private String configKey;

    @Schema(description = "配置值")
    private String configValue;

    @Schema(description = "值类型: STRING/NUMBER/BOOLEAN/JSON")
    private String valueType;

    @Schema(description = "默认值")
    private String defaultValue;

    @Schema(description = "配置项说明")
    private String description;

    @Schema(description = "是否对前端公开: 1 公开 / 0 仅后端")
    private Integer isPublic;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
