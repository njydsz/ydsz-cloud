package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 灰度桶视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgCanaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String canaryKey;
    private Integer bucketTotal;
    private String bucketSelected;
    private Integer percentage;
    private String experimentTemplateCode;
    private String experimentChannel;
    private String status;
    private String description;
    private String createdBy;
    private java.time.LocalDateTime createdAt;
    private String updatedBy;
    private java.time.LocalDateTime updatedAt;
}
