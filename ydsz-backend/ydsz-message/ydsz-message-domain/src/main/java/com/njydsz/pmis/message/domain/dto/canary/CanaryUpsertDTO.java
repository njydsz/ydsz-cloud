package com.njydsz.message.domain.dto.canary;


import lombok.Data;

/**
 * 灰度桶新增/更新 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class CanaryUpsertDTO {

    /** 灰度键(如 template_code 或 biz_type) */
    private String canaryKey;

    /** 桶总数(默认 100) */
    private Integer bucketTotal;

    /** 灰度比例(0-100) */
    private Integer percentage;

    /** 灰度命中后切换的实验模板编码(可空,空则不切换) */
    private String experimentTemplateCode;

    /** 灰度命中后切换的实验通道(可空,空则不切换) */
    private String experimentChannel;

    /** 状态: ENABLED/DISABLED */
    private String status;

    /** 描述说明 */
    private String description;
}
