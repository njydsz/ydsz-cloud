package com.njydsz.pmis.execution.dto;

import lombok.Data;

/**
 * 质保期终止请求
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class WarrantyTerminateDTO {
    /** 质保单ID */
    private Long id;
    /** 提前终止原因 */
    private String reason;
}
