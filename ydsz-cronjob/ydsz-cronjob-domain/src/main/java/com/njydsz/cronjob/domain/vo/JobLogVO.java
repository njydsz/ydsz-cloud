package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * JobLog 视图对象。
 *
 * <p>用于 Controller 层返回任务执行日志数据，对应实体 {@link com.njydsz.cronjob.domain.entity.log.JobLog}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobLogVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** 任务 ID */
  private String jobId;

  /** 任务 KEY */
  private String jobKey;

  /** 开始时间 */
  private LocalDateTime startTime;

  /** 结束时间 */
  private LocalDateTime endTime;

  /** 耗时(毫秒) */
  private Long durationMs;

  /** 错误信息 */
  private String errorMessage;

  /** 参数 JSON */
  private String paramsJson;

  /** 结果 JSON */
  private String resultJson;

  /** 链路追踪 ID */
  private String traceId;

  /** 触发类型: CRON 定时 / MANUAL 手动 / RETRY 重试 / MISFIRED Misfire 触发 */
  private String triggerType;

  /** 持锁者标识（hostname:pid） */
  private String lockHolder;

  /** 执行节点 ID（hostname:port） */
  private String execNodeId;

  /** 执行线程 ID */
  private Long execThreadId;

  /** 分片索引（0-based，非分片任务为 null） */
  private Integer shardIndex;

  /** 分片总数（非分片任务为 null） */
  private Integer shardTotal;

  /** 慢任务标记（0=非慢 / 1=慢） */
  private Integer isSlow;

  /** 慢任务阈值快照（毫秒） */
  private Long slowThresholdMs;

  /** 入队时间（任务被扫描并入队的时刻） */
  private LocalDateTime queueTime;

  /** 派发时间（任务被派发的时刻） */
  private LocalDateTime dispatchTime;

  /** Handler 初始化时间 */
  private LocalDateTime handlerInitTime;

  /** Handler 执行结束时间 */
  private LocalDateTime handlerEndTime;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
