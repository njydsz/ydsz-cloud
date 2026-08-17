package com.njydsz.cronjob.infra.entity.log;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * 任务执行日志内容（P0-2 在线日志白屏化）。
 *
 * <p>对应 {@code ydsz_job_log_content} 表，存储任务执行过程中业务侧通过 {@link com.njydsz.common.job.JobLogger}
 * 写入的逐行日志。 与 {@link JobLogDO}（执行级汇总）互补，本表为行级明细，供前端 SSE 实时滚动展示。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_log_content")
public class JobLogContentDO extends MpBaseIdEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务执行日志 ID（关联 ydsz_job_log.id） */
  private String logId;

  /** 任务 KEY（冗余，避免连表查询） */
  private String jobKey;

  /** 行号（从 1 递增） */
  private Integer lineNo;

  /** 日志级别：DEBUG / INFO / WARN / ERROR */
  private String logLevel;

  /** 日志内容（单行文本，最长 4000 字符） */
  private String content;
}
