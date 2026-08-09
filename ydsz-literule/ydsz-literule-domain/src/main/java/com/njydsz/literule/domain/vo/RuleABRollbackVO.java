package com.njydsz.literule.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 规则 A/B 测试回滚记录视图对象（VO）。
 * <p>
 * 用于 Controller 层返回灰度发布回滚操作的完整记录，包含触发原因、
 * 回滚时的错误率和样本量、操作人及通知状态，支撑灰度回滚审计追溯。
 * </p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleABRollbackVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 回滚记录唯一标识（主键） */
    private String id;
    /** 关联的规则编码 */
    private String ruleCode;
    /** 触发回滚的原因（ERROR_RATE_EXCEEDED/MANUAL/SAMPLE_INSUFFICIENT） */
    private String triggerReason;
    /** 回滚时的错误率 */
    private BigDecimal errorRate;
    /** 回滚时的样本量 */
    private Long sampleSize;
    /** 是否从灰度版本回滚 */
    private Boolean fromCanary;
    /** 操作人 */
    private String operator;
    /** 通知状态（SUCCESS/FAILED/NOT_SENT） */
    private String notifyStatus;
    /** 创建人 */
    private String createdBy;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新人 */
    private String updatedBy;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
