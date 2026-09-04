package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * P2-3 流程实例归档实体
 *
 * <p>对应数据库表 {@code ydsz_flow_his_instance}，存储已完成且超过 retention 天数的实例冷数据。
 *
 * <p><b>归档机制（P2-3）：</b>
 *
 * <ul>
 *   <li>由 {@code FlowArchiveScheduler} 周期执行（每日凌晨 2 点）
 *   <li>迁移规则：实例终态（{@code APPROVED/REJECTED/CANCELED}）且 {@code endAt} 超过 {@code
 *       workflow.archive.retention-days}（默认 180 天）
 *   <li>从 {@code ydsz_flow_instance} 复制到归档表，源表保留 30 天观察期后清理
 *   <li>归档表单独按月分区（{@code ydsz_flow_his_instance_2026m07}），便于历史数据按月清理
 * </ul>
 *
 * <p><b>字段说明：</b>与 {@link FlowInstance} 字段一一对应（去掉运行态字段）， 新增 {@code archivedAt} 记录归档时间。
 * 继承 {@link MpBaseEntity}（含审计/乐观锁/租户字段），与运行实例表字段对齐，归档时完整保留审计信息。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>唯一索引 {@code uk_business_type_id}（{@code business_type}, {@code business_id}）
 *   <li>普通索引 {@code idx_archived_at}（{@code archived_at}）
 *   <li>普通索引 {@code idx_end_at}（{@code end_at}）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowInstance 流程实例
 * @see FlowArchiveScheduler 归档调度器
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_his_instance")
public class FlowHisInstance extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程编码 */
  private String flowCode;

  /** 流程名称（冗余） */
  private String flowName;

  /** 流程定义 ID */
  private String definitionId;

  /** 流程版本 */
  private String flowVersion;

  /** 业务类型 */
  private String businessType;

  /** 业务单据 ID */
  private String businessId;

  /** 业务单据编号 */
  private String businessNo;

  /** 流程标题 */
  private String title;

  /** 发起人 ID */
  private String initiatorId;

  /** 发起人姓名（冗余） */
  private String initiatorName;

  /** 当前节点编码（终态时为结束节点编码） */
  private String currentNodeCode;

  /** 当前节点名称（冗余） */
  private String currentNodeName;

  /** 流程变量 JSON 快照 */
  private String variable;

  /** 终态（{@code APPROVED} / {@code REJECTED} / {@code CANCELED}） */
  private String flowStatus;

  /** 激活状态（终态时固定为 0） */
  private Integer activityStatus;

  /** 启动时间 */
  private LocalDateTime startAt;

  /** 结束时间 */
  private LocalDateTime endAt;

  /** 流程耗时（毫秒） */
  private Long durationMs;

  /** 归档时间（由调度器在迁移时填充） */
  private LocalDateTime archivedAt;

  /** 链路追踪 ID（保留原始 trace 便于历史回溯） */
  private String providerTraceId;
}
