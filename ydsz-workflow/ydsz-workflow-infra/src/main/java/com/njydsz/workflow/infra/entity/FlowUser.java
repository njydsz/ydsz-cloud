package com.njydsz.workflow.infra.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 流程任务-办理人关系实体
 *
 * <p>对应数据库表 {@code ydsz_flow_user}，
 * 存储「任务-办理人」的多对多关系（会签多办理人、加签、减签等场景）。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>一个 task 对应多个 user（如「部门负责人审批」会展开为部门内所有负责人）
 *   <li>记录每个办理人的处理状态（{@code processed}）与处理时间（{@code processAt}）
 *   <li>支持加签/减签：插入新 user 记录或软删除（{@code processed} 标记为 {@code 0}）
 * </ul>
 *
 * <p><b>加签类型（{@code signType}）：</b>
 *
 * <ul>
 *   <li>{@code ORIGINAL}：流程定义中配置的原始审批人（默认）
 *   <li>{@code BEFORE}：前加签插入的审批人（在原始人之前办理）
 *   <li>{@code AFTER}：后加签插入的审批人（在原始人之后办理）
 *   <li>{@code PARALLEL}：并加签插入的审批人（与原审批人并行，所有人审完才推进）
 *   <li>{@code ADD}：追加处理人（与原审批人并行，任一办完即推进）
 * </ul>
 *
 * <p><b>加权会签（{@code weight}）：</b>用于加权投票场景，{@code 已通过权重 / 总权重 >= votePassRate} 即判定通过。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>唯一索引 {@code uk_task_user}（{@code task_id}, {@code user_id}, {@code sign_type}）
 *   <li>普通索引 {@code idx_instance}（{@code instance_id}）：实例办理人清单
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowRunTask 流程待办
 * @see com.njydsz.workflow.domain.enums.FlowSignType 加签类型枚举
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_user")
public class FlowUser extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务 ID（关联 {@code ydsz_flow_run_task.id}） */
  private String taskId;

  /** 流程实例 ID（冗余便于查询） */
  private String instanceId;

  /** 节点编码 */
  private String nodeCode;

  /**
   * 用户类型。
   *
   * <p>取值：{@code USER}（具体用户）/ {@code ROLE}（角色展开）/ {@code DEPT}（部门展开）。 实际办理人由引擎展开后写入本表，展开前的规则仍存储在
   * {@code ydsz_flow_run_task.permission_flag}。
   */
  private String userType;

  /** 用户/角色/部门 ID */
  private String userId;

  /** 用户姓名（冗余） */
  private String userName;

  /** 是否已处理：{@code 0} 否 / {@code 1} 是 */
  private Integer processed;

  /** 处理时间 */
  private LocalDateTime processAt;

  /** 审批意见 */
  private String comment;

  /** 办理人权重（默认 {@code 1}，可配置 {@code 2/3} 等，用于加权会签） */
  private Integer weight;

  /**
   * 加签类型标识，区分原始审批人与动态加签人。
   *
   * <p>取值对齐 {@link com.njydsz.workflow.domain.enums.FlowSignType} 枚举，持久化使用 {@code name()}。
   *
   * <ul>
   *   <li>{@code ORIGINAL}：流程定义中配置的原始审批人（默认，向后兼容存量数据）
   *   <li>{@code BEFORE}：前加签插入的审批人
   *   <li>{@code AFTER}：后加签插入的审批人
   *   <li>{@code PARALLEL}：并加签插入的审批人（与原审批人并行，所有人审完才推进）
   *   <li>{@code ADD}：追加处理人
   * </ul>
   */
  private String signType;

  /** 链路追踪 ID */
  private String providerTraceId;
}
