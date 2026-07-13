package com.njydsz.pmis.common.chaos;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

/**
 * 混沌实验配置。
 *
 * <p>定义一个混沌工程实验的配置，包括实验目标、注入类型、触发条件等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ChaosExperiment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 实验目标标识（如 service:method、api:/path） */
    @NotBlank
    private String target;

    /** 注入类型（LATENCY、EXCEPTION、NULL_RESPONSE、CPU_LOAD、MEMORY_LEAK） */
    private String injectionType;

    /** 注入参数（如延迟毫秒数、异常类名等） */
    private String injectionValue;

    /** 触发概率（0~100，表示百分比） */
    private Integer triggerRate = 100;

    /** 是否启用 */
    private Boolean enabled = true;

    /** 实验描述 */
    private String description;
}
