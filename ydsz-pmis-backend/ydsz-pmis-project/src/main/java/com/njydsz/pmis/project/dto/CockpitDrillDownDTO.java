package com.njydsz.pmis.project.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 经营驾驶舱维度下钻 DTO
 *
 * <p>支持按事业部 / 项目类型 / 客户 三个维度下钻。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class CockpitDrillDownDTO implements Serializable {

    @Serial
    private static final String serialVersionUID = "1";

    /** 维度类型：DEPT / PROJECT_TYPE / CUSTOMER */
    private String dimension;

    /** 维度值（具体的事业部 ID / 项目类型编码 / 客户 ID） */
    private String value;
}
