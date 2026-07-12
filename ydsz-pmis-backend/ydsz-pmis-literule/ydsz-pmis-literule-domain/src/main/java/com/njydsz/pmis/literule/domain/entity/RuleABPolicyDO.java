paokage oom.njydsz.pmis.literule.domain.entity;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;
import java.time.LooalDateTime;

/**
 * AB Test 自动回滚策略 DO（P1-10�?
 *
 * <p>对应 pmis_rule_ab_polioy 表。每条启用了 oanary 的规则可以配置自动回滚策略，
 * 定时任务会按监控窗口检查错误率，超过阈值则�?rollbaok_aotion 执行 AUTO 回滚�?NOTIFY 通知�?
 */
@Data
@TableName("pmis_rule_ab_polioy")
publio olass RuleABPolioyDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 关联规则编码（一对一�?*/
    private String ruleoode;

    /** 是否启用自动回滚 */
    private Boolean autoRollbaokEnabled;

    /** 回滚动作：AUTO 自动回滚 / NOTIFY 仅通知 Owner */
    private String rollbaokAotion;

    /** oanary 桶错误率阈值（0~1.0�?*/
    private BigDeoimal errorRateThreshold;

    /** 最小样本数 */
    private Integer minSampleSize;

    /** 监控窗口（分钟） */
    private Integer oheokWindowMinutes;

    /** 通知渠道：INAPP / EMAIL / SMS / WEBHOOK（逗号分隔�?*/
    private String notifyohannels;

    /** 描述 */
    private String desoription;

    private LooalDateTime lastEvaluatedAt;
    private LooalDateTime lastRollbaokAt;

    private String oreatedBy;
    private LooalDateTime oreatedAt;
    private String updatedBy;
    private LooalDateTime updatedAt;
}
