package com.njydsz.cronjob.domain.entity.export;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 异步导出任务实体（domain 层持久化实体，YDIZ-DDD-007 单包模式）。
 *
 * <p>支撑大数据量异步导出场景（如定时任务列表导出、日志导出等），
 * 任务提交后由 Worker 异步处理，完成后生成可下载文件链接。
 *
 * @author ydsz-team
 * @since 26.10.13
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_comm_export_task")
public class ExportTask extends MpBaseEntity<String> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 来源模块标识（如 cronjob/workflow/message） */
  @TableField("module")
  private String module;

  /** 任务名称（展示用） */
  @TableField("task_name")
  private String taskName;

  /** 导出任务类型（如 JOB_EXPORT/LOG_EXPORT） */
  @TableField("task_type")
  private String taskType;

  /** 任务参数（JSON） */
  @TableField("params")
  private String params;

  /** 当前进度百分比 0-100 */
  @TableField("progress_percent")
  private Integer progressPercent;

  /** 失败原因 */
  @TableField("error_message")
  private String errorMessage;

  /** 生成文件名称 */
  @TableField("file_name")
  private String fileName;

  /** 文件大小（字节） */
  @TableField("file_size")
  private Long fileSize;

  /** 存储桶名 */
  @TableField("storage_bucket")
  private String storageBucket;

  /** 存储键 */
  @TableField("storage_key")
  private String storageKey;

  /** MIME 类型 */
  @TableField("mime_type")
  private String mimeType;

  /** 临时下载链接 */
  @TableField("download_url")
  private String downloadUrl;

  /** 过期时间 */
  @TableField("expire_at")
  private LocalDateTime expireAt;

  /** 开始处理时间 */
  @TableField("started_at")
  private LocalDateTime startedAt;

  /** 完成时间 */
  @TableField("completed_at")
  private LocalDateTime completedAt;

  /** 已重试次数 */
  @TableField("retry_count")
  private Integer retryCount;

  /** 最大重试次数 */
  @TableField("max_retry")
  private Integer maxRetry;
}
