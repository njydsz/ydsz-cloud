package com.njydsz.pmis.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工具市场查询 DTO（P2-12 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-12)
 */
@Data
@Schema(description = "工具市场查询条件")
public class ToolMarketQueryDTO {

    /** 工具名称（模糊匹配） */
    @Schema(description = "工具名称（模糊匹配）")
    private String toolName;

    /** 工具分类 */
    @Schema(description = "工具分类")
    private String category;

    /** 来源类型：MANUAL / OPENAPI */
    @Schema(description = "来源类型")
    private String sourceType;

    /** 是否启用 */
    @Schema(description = "是否启用")
    private Boolean enabled;

    /** 页码（默认 1） */
    @Schema(description = "页码", example = "1")
    private Integer page;

    /** 每页大小（默认 20） */
    @Schema(description = "每页大小", example = "20")
    private Integer size;
}
