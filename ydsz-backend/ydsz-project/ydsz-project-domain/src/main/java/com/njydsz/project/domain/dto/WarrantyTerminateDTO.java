package com.njydsz.project.domain.dto;

import lombok.Data;

/**
 * 质保期终止请求
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class WarrantyTerminateDTO {
    /** 质保单ID */
    private String id;
    /** 提前终止原因 */
    private String reason;
}
