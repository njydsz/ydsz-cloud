package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典项 VO。
 *
 * @author ydsz-team
 */
@Data
@Schema(description = "字典项视图对象")
public class DictItemVO {

    @Schema(description = "主键 ID")
    private String id;

    @Schema(description = "所属字典类型编码")
    private String typeCode;

    @Schema(description = "字典项编码")
    private String itemCode;

    @Schema(description = "字典项展示值")
    private String itemValue;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "父级字典项 ID（0=根）")
    private String parentId;

    @Schema(description = "字典项业务说明")
    private String description;

    @Schema(description = "扩展属性 JSON")
    private String extJson;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
