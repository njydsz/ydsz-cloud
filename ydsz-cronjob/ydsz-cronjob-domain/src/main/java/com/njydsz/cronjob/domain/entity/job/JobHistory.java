package com.njydsz.cronjob.domain.entity.job;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * 任务配置历史版本实体（P1-6 任务版本管理）。
 *
 * <p>对应 {@code ydsz_job_history} 表，每次任务配置更新前自动保存一份完整 JSON 快照， 支持版本列表查询、版本对比和一键回滚。回滚操作会基于历史快照恢复配置字段，
 * 同时保留当前任务的统计字段（触发次数等），并产生新的历史版本。
 *
 * <p><b>YDIZ-OOP-006 合规：</b>{@code isDeleted}（primitive {@code boolean}）+
 * {@link TableLogic} 映射 DB 列 {@code is_deleted}，Lombok 生成 {@code isDeleted()} getter，
 * 与 MyBatis-Plus 逻辑删除约定一致。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_history")
public class JobHistory extends MpBaseIdEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务 ID（关联 ydsz_job.id） */
  private String jobId;

  /** 版本号（对应更新前的 job.version） */
  private Integer version;

  /** 完整 Job JSON 快照（变更后状态; DELETE 时为 NULL） */
  private String snapshot;

  /** 变更类型: CREATE / UPDATE / DELETE */
  private String changeType;

  /** 变更前快照 JSON（CREATE 时为 NULL; UPDATE/DELETE 时记录变更前状态） */
  private String beforeSnapshot;

  /** 变更说明（如"任务创建"、"任务更新"、"任务删除"） */
  private String changeRemark;

  /** 任务名称（冗余，便于列表展示） */
  private String jobName;

  /** 任务 KEY（冗余） */
  private String jobKey;

  /** 处理器（冗余） */
  private String handler;

  /** Cron 表达式（冗余） */
  private String cronExpression;

  /** 参数 JSON（冗余） */
  private String paramsJson;

  /** 备注（冗余） */
  private String remark;

  /** 修改人 ID */
  private String changedBy;

  /** 修改时间 */
  private LocalDateTime changedAt;

  /**
   * 是否逻辑删除（DB 列为 {@code is_deleted}，0=未删除，1=已删除）。
   *
   * <p>YDIZ-OOP-006 合规：{@code isDeleted}（primitive {@code boolean}）+
   * {@link TableLogic} 自动映射 {@code is_deleted} 列，Lombok 生成 {@code isDeleted()} getter。
   */
  @TableLogic private boolean isDeleted;
}
