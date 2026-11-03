package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 异步导出任务视图对象（YDIZ-DDD-007：server/web 层禁用 domain Entity）。
 *
 * <p>用于 Controller 层返回任务详情与列表，字段与 {@link
 * com.njydsz.cronjob.domain.entity.export.ExportTask} 对齐但不参与持久化。
 *
 * @author ydsz-team
 * @since 26.10.13
 */
@Data
public class ExportTaskVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** 来源模块标识 */
  private String module;

  /** 任务名称 */
  private String taskName;

  /** 导出任务类型 */
  private String taskType;

  /** 任务参数（JSON） */
  private String params;

  /** 当前进度百分比 0-100 */
  private Integer progressPercent;

  /** 失败原因 */
  private String errorMessage;

  /** 生成文件名称 */
  private String fileName;

  /** 文件大小（字节） */
  private Long fileSize;

  /** 存储桶名 */
  private String storageBucket;

  /** 存储键 */
  private String storageKey;

  /** MIME 类型 */
  private String mimeType;

  /** 临时下载链接 */
  private String downloadUrl;

  /** 过期时间 */
  private LocalDateTime expireAt;

  /** 开始处理时间 */
  private LocalDateTime startedAt;

  /** 完成时间 */
  private LocalDateTime completedAt;

  /** 状态（PENDING/PROCESSING/SUCCEEDED/FAILED/CANCELED） */
  private String status;

  /** 租户 ID */
  private String tenantId;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
