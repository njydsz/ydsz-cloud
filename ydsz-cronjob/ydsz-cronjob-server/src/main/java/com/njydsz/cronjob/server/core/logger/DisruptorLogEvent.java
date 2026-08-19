package com.njydsz.cronjob.server.core.logger;

import com.njydsz.cronjob.infra.entity.log.JobLogContent;

/**
 * P2-3: Disruptor 日志事件（预分配对象，避免 GC）。
 *
 * <p>作为 Disruptor ring buffer 的事件载体，由 {@link DisruptorLogEventFactory} 预分配，
 * 被 {@link DisruptorLogPublisher} 发布，由 {@link DisruptorLogEventHandler} 消费并写入 DB。
 *
 * <p>事件生命周期：
 *
 * <ol>
 *   <li>执行线程调用 {@link #setInfo} 填充日志信息
 *   <li>Disruptor 发布事件
 *   <li>消费者线程读取事件并写入 DB
 *   <li>事件对象自动复用（ring buffer 循环使用）
 * </ol>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class DisruptorLogEvent {

  /** 日志 ID */
  private String logId;

  /** 任务 KEY */
  private String jobKey;

  /** 行号 */
  private int lineNo;

  /** 日志内容 */
  private String content;

  /** 日志级别：INFO / WARN / ERROR / DEBUG */
  private String level;

  /** 写入时间戳 */
  private long timestamp;

  /** 清空事件数据（复用前调用） */
  public void clear() {
    this.logId = null;
    this.jobKey = null;
    this.lineNo = 0;
    this.content = null;
    this.level = null;
    this.timestamp = 0L;
  }

  /**
   * 填充日志事件信息。
   *
   * @param logId 日志 ID
   * @param jobKey 任务 KEY
   * @param lineNo 行号
   * @param content 日志内容
   * @param level 日志级别
   */
  public void setInfo(String logId, String jobKey, int lineNo, String content, String level) {
    this.logId = logId;
    this.jobKey = jobKey;
    this.lineNo = lineNo;
    this.content = content;
    this.level = level;
    this.timestamp = System.currentTimeMillis();
  }

  /**
   * 转换为 JobLogContent 实体（消费者调用）。
   *
   * @return JobLogContent 实例
   */
  public JobLogContent toLogContent() {
    JobLogContent logContent = new JobLogContent();
    logContent.setLogId(this.logId);
    logContent.setJobKey(this.jobKey);
    logContent.setLineNo(this.lineNo);
    logContent.setContent(this.content);
    logContent.setLogLevel(this.level);
    return logContent;
  }

  public String getLogId() {
    return logId;
  }

  public String getJobKey() {
    return jobKey;
  }

  public int getLineNo() {
    return lineNo;
  }

  public String getContent() {
    return content;
  }

  public String getLevel() {
    return level;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
