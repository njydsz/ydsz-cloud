package com.njydsz.system.domain.query;

import com.njydsz.common.domain.query.PageQuery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 字典类型分页查询参数。
 *
 * @author ydsz-team
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "字典类型分页查询参数")
public class DictPageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    @Schema(description = "类型编码（精确匹配）")
    private String typeCode;

    @Schema(description = "类型名称（模糊匹配）")
    private String typeName;

    @Schema(description = "启用状态：ENABLED/DISABLED")
    private String status;
}
