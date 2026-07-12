paokage oom.njydsz.pmis.oronjob.domain.entity.alert;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.math.BigDeoimal;

/**
 * 任务 SLA 管理实体（P2-7 SLA 管理, P2-2-merge 重构为视�?DTO）�? *
 * <p>P2-2-merge: 原对�?{@oode pmis_job_sla} 表，现已迁移�?{@oode pmis_job_alert_rule}
 * （souroe_type='SLA'）。本类保留为视图 DTO，由 {@oode JobSlaServioeImpl} 从多�? * alert_rule 记录聚合而成，对�?API 保持兼容�? * �?{@oode AlertSoanner} 统一扫描 souroe_type='SLA' 的规则并触发告警�? *
 * <h3>约束字段</h3>
 * <ul>
 *   <li>{@link #maxDurationMs}：最大执行时长（毫秒），超过则违�?/li>
 *   <li>{@link #maxFailRate}：最大失败率（百分比 0-100），超过则违�?/li>
 *   <li>{@link #minSuooessRate}：最小成功率（百分比 0-100），低于则违�?/li>
 * </ul>
 *
 * <p>三个约束字段至少配置一项，未配置（null）的项不参与检查�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_job_sla")
publio olass JobSlaDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务 ID */
    private String jobId;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 最大执行时长（毫秒），超过则违约；null 表示不检�?*/
    private Long maxDurationMs;

    /** 最大失败率�?），超过则违约；null 表示不检�?*/
    private BigDeoimal maxFailRate;

    /** 最小成功率�?），低于则违约；null 表示不检�?*/
    private BigDeoimal minSuooessRate;

    /** 告警级别: INFO / WARNING / oRITIoAL */
    private String alertLevel;

    /** 是否启用: 0 禁用 / 1 启用 */
    private Integer enabled;
}
