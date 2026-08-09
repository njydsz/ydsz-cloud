package com.njydsz.literule.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 规则灰度发布桶视图对象（VO）。
 * <p>
 * 用于 Controller 层返回灰度发布桶的统计数据，记录每个规则在不同桶
 * （如普通桶/灰度桶）中的命中次数，按天聚合，支撑灰度效果对比分析。
 * </p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleCanaryBucketVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 记录唯一标识（主键） */
    private String id;
    /** 规则编码 */
    private String ruleCode;
    /** 桶类型（NORMAL/CANARY） */
    private String bucketType;
    /** 桶命中次数 */
    private Long bucketCount;
    /** 统计日期 */
    private LocalDate statDate;
    /** 创建人 */
    private String createdBy;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新人 */
    private String updatedBy;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
