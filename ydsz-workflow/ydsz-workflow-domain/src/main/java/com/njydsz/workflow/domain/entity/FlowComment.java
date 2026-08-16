package com.njydsz.workflow.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * P2-2: 流程评论实体
 *
 * <p>对应数据库表 {@code ydsz_flow_comment}，对标钉钉/飞书审批评论区。 独立于 {@link FlowAuditLog}（审计日志是操作轨迹、不可变），
 * 评论是讨论（可回复、可删除），关注点正交。
 *
 * <p><b>与审计日志的区别：</b>
 *
 * <ul>
 *   <li><b>审计日志（{@link FlowAuditLog}）</b>：系统视角，记录「谁在什么时间做了什么」， 用于合规审计、问题回溯，<b>不可修改、不可删除</b>
 *   <li><b>评论（{@code FlowComment}）</b>：用户视角，记录「审批人之间的沟通讨论」， <b>支持回复、编辑、删除</b>（仅评论本人或管理员可删）
 * </ul>
 *
 * <p><b>多级回复机制：</b>
 *
 * <ul>
 *   <li>一级评论：{@code parentCommentId = null}
 *   <li>二级及以下回复：{@code parentCommentId} 指向父评论 ID， {@code replyToUserId} 标记被回复人（同一父评论下可回复不同人）
 * </ul>
 *
 * <p><b>评论类型：</b>
 *
 * <ul>
 *   <li>{@code COMMENT}：普通评论（默认）
 *   <li>{@code QUESTION}：提问（@某人需要回答）
 *   <li>{@code REPLY}：回复（{@code parentCommentId} 非空时）
 * </ul>
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>普通索引 {@code idx_instance}（{@code instance_id}）：流程评论时间线
 *   <li>普通索引 {@code idx_parent}（{@code parent_comment_id}）：子评论查询
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowAuditLog 流程审计日志
 * @see com.njydsz.workflow.server.service.FlowCommentService 评论服务
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_comment")
public class FlowComment extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 关联流程实例 ID */
  private String instanceId;

  /** 关联任务 ID（实例级评论可为空） */
  private String taskId;

  /** 关联节点编码（任务级评论时记录所在节点） */
  private String nodeCode;

  /** 评论人 ID */
  private String userId;

  /** 评论人姓名（冗余） */
  private String userName;

  /** 评论内容（{@code TEXT} 类型，最大长度 2000） */
  private String content;

  /** 评论类型：{@code COMMENT} / {@code QUESTION} / {@code REPLY}（默认 {@code COMMENT}） */
  private String type;

  /** 父评论 ID（一级评论为 {@code null}） */
  private String parentCommentId;

  /** 被回复人 ID（回复某条评论时标记，一级评论为 {@code null}） */
  private String replyToUserId;

  /** 被回复人姓名（冗余） */
  private String replyToUserName;

  /** 链路追踪 ID */
  private String providerTraceId;
}
