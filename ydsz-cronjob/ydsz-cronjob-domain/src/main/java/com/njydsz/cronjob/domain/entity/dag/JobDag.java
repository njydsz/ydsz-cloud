package com.njydsz.cronjob.domain.entity.dag;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * DAG 工作流定义实体（ydsz_job_dag 表，P2 DAG 增强）。
 *
 * <p>将 DAG 提升为一等公民：一个 DAG 定义包含若干任务节点和依赖边， 支持手动触发或 Cron 定时触发整个工作流。
 *
 * <p>{@link #dagDefinition} 为 JSON 格式，包含节点列表、边列表及前端可视化坐标， 由 {@code DagDefinition} 模型类序列化/反序列化。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_dag")
public class JobDag extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** DAG 唯一 KEY（调度与触发使用） */
  @NotBlank(message = "{validation.cronjob.msg_dag_key_required}")
  private String dagKey;

  /** DAG 名称（展示用） */
  @NotBlank(message = "{validation.cronjob.msg_dag_name_required}")
  private String dagName;

  /** DAG 定义 JSON（nodes + edges + 可视化坐标） */
  @NotBlank(message = "{validation.cronjob.msg_dag_definition_required}")
  private String dagDefinition;

  /** DAG 状态: DRAFT 草稿 / ENABLED 启用 / DISABLED 禁用 */
  private String dagStatus;

  /** 触发类型: MANUAL 手动 / CRON 定时 */
  private String triggerType;

  /** Cron 表达式（triggerType=CRON 时必填） */
  private String cronExpression;

  /** 最大并发实例数(0=不限制, 默认1) */
  private Integer maxConcurrentInstances;

  /** DAG 级失败策略: FAIL_FAST 中止 / CONTINUE_ON_FAIL 继续 */
  private String failStrategy;

  /** DAG 描述 */
  private String description;

  /**
   * P1-3: DAG 超时时间（毫秒，null=不限时）。
   *
   * <p>整个 DAG 工作流从启动到完成的最大允许耗时。超时后 DAG 实例标记为 TIMEOUT， 运行中的节点根据 failStrategy 决定是否中止。
   */
  private Long timeoutMs;

  /** 下次触发时间（CRON 模式） */
  private LocalDateTime nextFireTime;

  /** 上次触发时间 */
  private LocalDateTime lastFireTime;

  /** 总触发次数 */
  private Long fireCount;

  /** 成功次数 */
  private Long successCount;

  /** 失败次数 */
  private Long failCount;

  /** 版本号(乐观锁) */
  private Integer version;
}
