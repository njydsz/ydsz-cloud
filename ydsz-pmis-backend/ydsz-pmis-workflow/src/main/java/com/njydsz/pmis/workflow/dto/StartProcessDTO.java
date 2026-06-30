package com.njydsz.pmis.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 启动流程 DTO
 */
@Data
public class StartProcessDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程定义 KEY */
    @NotBlank(message = "流程定义 KEY 不能为空")
    private String processKey;

    /** 业务类型 */
    @NotBlank(message = "业务类型不能为空")
    private String businessType;

    /** 业务单据 ID */
    @NotBlank(message = "业务单据 ID 不能为空")
    private String businessId;

    /** 业务单据编号 */
    private String businessNo;

    /** 流程标题 */
    private String title;

    /** 发起人 ID（可选，默认从上下文取） */
    private Long initiatorId;

    /** 发起人姓名 */
    private String initiatorName;

    /** 业务变量（流程启动时的变量） */
    private Map<String, Object> variables;
}
