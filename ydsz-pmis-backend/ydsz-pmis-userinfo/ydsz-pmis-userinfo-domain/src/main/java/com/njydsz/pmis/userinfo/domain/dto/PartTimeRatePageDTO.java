package com.njydsz.pmis.userinfo.domain.dto.rate;

import com.njydsz.pmis.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 兼职工时单价分页查询 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "兼职工时单价分页查询")
public class PartTimeRatePageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 级别段位: PRIMARY/MIDDLE/SENIOR/EXPERT/STRATEGIC */
    @Schema(description = "级别段位")
    private String levelSegment;

    /** 状态: ACTIVE/INACTIVE */
    @Schema(description = "状态")
    private String status;
}
