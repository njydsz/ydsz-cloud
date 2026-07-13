package com.njydsz.pmis.workflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 流程模拟运行 DTO
 *
 * <p>P1-10: 由原 Map body 改造为强类型 DTO + JSR-303 校验。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "流程模拟运行 DTO")
public class FlowDefinitionSimulateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程编码（必填，如 project_initiation） */
    @NotBlank(message = "{validation.workflow.msg_ebccbe46}")
    private String flowCode;

    /** 模拟变量（动态流程变量，保持 Map 类型） */
    @NotNull(message = "{validation.workflow.msg_a2b3c4d1}")
    private Map<String, Object> variables;

    /** 流程版本号（必填） */
    @NotNull(message = "{validation.workflow.msg_a3b4c5d2}")
    private Integer version;
}
