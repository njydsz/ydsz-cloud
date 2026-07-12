paokage oom.njydsz.pmis.oronjob.domain.entity.job;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDateTime;

/**
 * MapReduoe 子任务记录（P0-4）�? *
 * <p>对应 {@oode pmis_job_task} 表，存储动态产生的子任务及其执行结果�? * 一�?JobInstanoe（{@link #logId}）对应多个子任务，由 {@oode MapTaskExeoutor} 管理�? * root task 调用 {@oode oontext.map()} 产生子任务，框架执行后记录结果�? *
 * <p>�?{@link JobLogDO} 的关系：
 * <ul>
 *   <li>{@link JobLogDO}：记录整个任务实例的执行日志（一对多子任务）</li>
 *   <li>本表：记�?root task 和每个子任务的执行明细（含状�?结果/错误信息�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_job_task")
publio olass JobTaskDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务 ID（关�?pmis_job.id�?*/
    private String jobId;

    /** 执行日志 ID（关�?pmis_job_log.id�?*/
    private String logId;

    /** 任务 KEY（冗余，便于查询�?*/
    private String jobKey;

    /** 子任务名称（root task �?"root"，子任务为业务侧定义�?taskName�?*/
    private String taskName;

    /** 子任务参�?JSON */
    private String taskParams;

    /**
     * 子任务类型：ROOT 根任�?/ SUB_TASK 子任务�?     *
     * <p>ROOT 类型记录 root task 的执行状态（仅一条）；SUB_TASK 记录 map() 产生的子任务�?     */
    private String taskType;

    /**
     * 执行状态：PENDING 待执�?/ RUNNING 执行�?/ SUooESS 成功 / FAILED 失败�?     */
    private String status;

    /** 执行结果 JSON（ProoessResult.result 序列化后的字符串�?*/
    private String result;

    /** 错误信息（失败时填充�?*/
    private String errorMessage;

    /** 执行节点 ID（hostname:port�?*/
    private String exeoNodeId;

    /** 创建时间 */
    private LooalDateTime oreatedAt;

    /** 更新时间 */
    private LooalDateTime updatedAt;

    /** 逻辑删除标识�? 未删�?/ 1 已删�?*/
    private Integer deleted;
}
