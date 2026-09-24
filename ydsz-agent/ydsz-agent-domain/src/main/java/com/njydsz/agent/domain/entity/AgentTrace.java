package com.njydsz.agent.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Agent 执行链路（domain 层持久化实体，YDIZ-DDD-007 单包模式）
 *
 * <p>记录一次 Agent 执行的完整元数据，包括所属对话、Agent 类型、执行状态与总耗时。
 * 步骤明细存储在 {@code ydsz_agt_trace_step} 表中，通过 traceId 关联。
 *
 * <p><b>YDIZ-DDD-007</b>：domain Entity 直接携带 MyBatis-Plus ORM 注解，
 * infra 层通过依赖 domain 模块引用本类，禁止自建 PO/DO 副本。
 * 主键 {@code id} 映射到数据库 {@code trace_id} 列（业务生成，非自增）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_trace")
public class AgentTrace extends MpBaseEntity<String> {

  private static final long serialVersionUID = 1L;

  /** 链路唯一 ID（主键，业务生成非自增，映射数据库 trace_id 列）。 */
  @TableId(type = IdType.INPUT)
  @TableField("trace_id")
  private String id;

  /** 所属对话 ID */
  private String conversationId;

  /** Agent 类型标识（CHAT/REACT/RAG/PLAN_EXECUTE/SUPERVISOR） */
  private String agentId;

  /** 总耗时（毫秒） */
  private Long totalDurationMs;
}
