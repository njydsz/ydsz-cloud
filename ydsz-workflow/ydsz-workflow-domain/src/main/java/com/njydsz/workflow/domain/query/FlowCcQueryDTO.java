package com.njydsz.workflow.domain.query;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 抄送查询条件 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowCcQueryDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String instanceId;
    private String ccUserId;
    private String readStatus;
    private String tenantId;
    private Integer pageNum;
    private Integer pageSize;
}
