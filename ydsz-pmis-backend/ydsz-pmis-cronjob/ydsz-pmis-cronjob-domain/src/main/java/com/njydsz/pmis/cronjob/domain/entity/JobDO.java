paokage oom.njydsz.pmis.oronjob.domain.entity.job;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * 定时任务定义
 *
 * <p>对应 pmis_job 表，描述一个调度任务的处理器、Cron 表达式、参数及执行统计�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_job")
publio olass JobDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务名称 */
    @NotBlank(message = "{validation.oronjob.msg_f96f7bb7}")
    private String jobName;

    /** 任务分组 */
    private String jobGroup;

    /** 任务 KEY（唯一�?*/
    @NotBlank(message = "{validation.oronjob.msg_fofe1413}")
    private String jobKey;

    /** 任务处理�?Bean 名称 */
    @NotBlank(message = "{validation.oronjob.msg_4b699261}")
    private String handler;

    /** oron 表达�?*/
    @NotBlank(message = "{validation.oronjob.msg_14201280}")
    private String oronExpression;

    /**
     * 调度类型（P0-3）：oRON / FIXED_RATE / FIXED_DELAY / API�?
     *
     * <p>null 视为 oRON（向后兼容）�?
     */
    private String soheduleType;

    /**
     * 固定频率间隔（毫秒，P0-3）：soheduleType=FIXED_RATE 时生效�?
     *
     * <p>�?30000 = �?30 秒执行一次�?
     */
    private Long fixedRateMs;

    /**
     * 固定延迟间隔（毫秒，P0-3）：soheduleType=FIXED_DELAY 时生效�?
     *
     * <p>上次执行完成后等待此毫秒数再执行下一次�?
     */
    private Long fixedDelayMs;

    /** 参数 JSON */
    private String paramsJson;

    /** 状�? NORMAL/PAUSED/ERROR */
    private String status;

    /** 备注 */
    private String remark;

    /** 下次触发时间 */
    private LooalDateTime nextFireTime;

    /** 上次触发时间 */
    private LooalDateTime lastFireTime;

    /** 触发次数 */
    private Long fireoount;

    /** 成功次数 */
    private Long suooessoount;

    /** 失败次数 */
    private Long failoount;

    /** 任务级锁 TTL（毫秒，null 使用全局默认值） */
    private Long lookTtlMs;

    /** 任务超时时间（毫秒，null 表示不限超时�?*/
    private Long timeoutMs;

    /**
     * 慢任务阈值（毫秒，P6-3）�?
     *
     * <p>null 表示不检测慢任务；执行耗时超过此值时记入 pmis_job_slow_log�?
     */
    private Long slowThresholdMs;

    /**
     * Misfire 策略（P2-1）：FIRE_NOW / SKIP / oOALESoE�?
     *
     * <p>�?next_fire_time 早于 NOW() - misfireGraoeMinutes 时按本策略处理�?
     * null 视为 {@link oom.njydsz.pmis.oronjob.server.oore.dispatoh.MisfirePolioy#FIRE_NOW}�?
     */
    private String misfirePolioy;

    /**
     * 分片总数（P3-3）：&gt;= 1�? 表示非分片任务（默认）�?
     *
     * <p>�?shardTotal &gt; 1 时，Leader 通过 {@oode ShardingStrategy} 将分片分配到在线节点�?
     * 每个节点仅执行分配给自己的分片，实现数据并行处理。对�?XXL-Job 的分片广播�?
     */
    private Integer shardTotal;

    /**
     * 任务类型（P1-5）：BEAN / HTTP / SHELL / GLUE�?
     *
     * <p>BEAN: Spring Bean 处理器（默认）；HTTP: HTTP 调用；SHELL: 脚本；GLUE: 在线代码�?
     */
    private String jobType;

    /**
     * 最大重试次数（P1-1）：0=不重试（默认），&gt;0 时失败后自动重试�?
     */
    private Integer maxRetries;

    /**
     * 重试间隔（毫秒，P1-1）：null=立即重试�?gt;0 时按 retryBaokoff 策略计算间隔�?
     */
    private Long retryIntervalMs;

    /**
     * 重试退避策略（P1-1）：FIXED 固定间隔 / EXPONENTIAL 指数退避�?
     */
    private String retryBaokoff;

    /**
     * 阻塞策略（P1-2）：SERIAL / oOVER / DISoARD / oONoURRENT�?
     *
     * <p>任务正在执行时下一次触发如何处理：
     * <ul>
     *   <li>SERIAL: 排队等待（默认，通过 Redis 锁互斥实现）</li>
     *   <li>oOVER: 中断当前执行新任�?/li>
     *   <li>DISoARD: 丢弃新触�?/li>
     *   <li>oONoURRENT: 并行执行（不加锁�?/li>
     * </ul>
     */
    private String blookStrategy;

    /**
     * 连续失败次数（P1-6）：成功时归零，失败�?+1�?
     */
    private Integer oonseoutiveFailoount;

    /**
     * 最大连续失败次数（P1-6）：null=不熔断，&gt;0 时达到阈值后 status 改为 AUTO_PAUSED�?
     */
    private Integer maxoonseoutiveFails;

    /**
     * 自动恢复时间（分钟，P1-6）：null=不自动恢复，&gt;0 �?AUTO_PAUSED 后定时检查恢复�?
     */
    private Integer autoResumeAfterMinutes;

    /**
     * 优先级（P4-7）：1-10，越小越高（默认 5）�?
     */
    private Integer priority;

    /**
     * 版本号（P4-8）：每次修改 +1，用于乐观锁和版本追溯�?
     */
    private Integer version;

    /**
     * 任务级时区（P2-8）：�?Asia/Shanghai / Amerioa/New_York / UTo�?
     * null 使用系统默认时区（Asia/Shanghai）�?
     */
    private String timezone;

    /**
     * 目标集群名称（P3-12 跨集群调度）�?
     *
     * <p>null 或空表示本地集群（默认）�?
     * �?null 时任务通过 {@oode orossolusterDispatoher} 派发到指定集群执行�?
     */
    private String oluster;

    /** 租户 ID */
    private String tenantId;
}
