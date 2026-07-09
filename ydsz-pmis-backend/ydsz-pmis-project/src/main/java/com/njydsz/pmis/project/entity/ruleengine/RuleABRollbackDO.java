package com.njydsz.pmis.project.entity.ruleengine;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AB Test 回滚历史 DO（P1-10）
 */
@Data
@TableName("pmis_rule_ab_rollback")
public class RuleABRollbackDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String ruleCode;

    /** 触发原因：ERROR_RATE / MANUAL / OWNER_REQUEST */
    private String triggerReason;

    private BigDecimal errorRate;
    private Long sampleSize;

    /** true=已从 canary 切换回主版本 / false=仅通知未回滚 */
    private Boolean fromCanary;

    private String operator;
    private String notifyStatus;

    private LocalDateTime createdAt;
}
