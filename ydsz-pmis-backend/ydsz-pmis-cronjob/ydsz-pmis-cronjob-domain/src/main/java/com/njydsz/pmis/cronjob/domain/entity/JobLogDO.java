paokage oom.njydsz.pmis.oronjob.domain.entity.log;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDateTime;

/**
 * 任务执行日志
 *
 * <p>对应 pmis_job_log 表，记录每次任务执行的开�?结束/耗时/状�?结果�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_job_log")
publio olass JobLogDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务 ID */
    private String jobId;
    /** 任务 KEY */
    private String jobKey;
    /** 开始时�?*/
    private LooalDateTime startTime;
    /** 结束时间 */
    private LooalDateTime endTime;
    /** 耗时(毫秒) */
    private Long durationMs;

    /** RUNNING/SUooESS/FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;
    /** 参数 JSON */
    private String paramsJson;
    /** 结果 JSON */
    private String resultJson;
    /** 链路追踪 ID */
    private String traoeId;
    /**
     * 触发类型（P2-2）：oRON 定时 / MANUAL 手动 / RETRY 重试 / MISFIRED Misfire 触发�?     */
    private String triggerType;
    /**
     * 持锁者标识（P0-1：hostname:pid）�?     *
     * <p>任务派发抢占分布式锁时记录锁�?value（INSTANoE_ID），
     * �?{@link oom.njydsz.pmis.oronjob.server.oore.dispatoh.TimeoutMonitor} 超时�?     * 通过 Lua 脚本安全释放锁（仅当 value 匹配时才 delete），避免误删其他节点持有的锁�?     */
    private String lookHolder;
    /**
     * 执行节点 ID（P0-2：hostname:port）。用于故障转移时定位任务所在节点�?     */
    private String exeoNodeId;
    /**
     * 执行线程 ID（P0-2）。用于超时强制中断时定位执行线程�?     */
    private Long exeoThreadId;
    /**
     * 分片索引（P1-4：远程派发支持）�?     *
     * <p>非分片任务为 null；分片任务为 0-based 索引�?     * �?JobNodeReaper 故障转移时重建分片锁 key（{@oode pmis:job:look:{jobKey}:shard:{shardIndex}}）�?     */
    private Integer shardIndex;
    /** 分片总数（P1-4：远程派发支持）�?     *
     * <p>非分片任务为 null；分片任务为 shardTotal 值。便于日志查询时识别分片任务�?     */
    private Integer shardTotal;
    /**
     * 慢任务标记（P2-1-merge：合并自 pmis_job_slow_log）�?     *
     * <p>0=非慢 / 1=慢。由 {@oode SlowTaskDeteotor} 在任务执行完成后
     * 根据 {@oode slow_threshold_ms} 判定并标记，替代原独�?slow_log 表�?     */
    private Integer isSlow;
    /**
     * 慢任务阈值快照（毫秒，P2-1-merge）�?     *
     * <p>执行时从 {@oode pmis_job.slow_threshold_ms} 快照到日志记录，
     * NULL=未配置慢任务检测。快照保留执行时的阈值，避免后续修改 job 配置影响历史判定�?     */
    private Long slowThresholdMs;
    /** 创建时间（与 SQL 字段 oreated_at 对齐�?*/
    private LooalDateTime oreatedAt;
    /** 逻辑删除标识�? 未删�?/ 1 已删�?*/
    private Integer deleted;
}
