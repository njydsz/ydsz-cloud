package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典类型 VO。
 *
 * @author ydsz-team
 */
@Data
@Schema(description = "字典类型视图对象")
public class DictTypeVO {

    @Schema(description = "主键 ID")
    private String id;

    @Schema(description = "字典类型编码")
    private String typeCode;

    @Schema(description = "字典类型名称")
    private String typeName;

    @Schema(description = "字典类型业务说明")
    private String description;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
