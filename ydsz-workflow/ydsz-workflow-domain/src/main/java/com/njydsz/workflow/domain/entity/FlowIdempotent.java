package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.common.jdbc.entity.MpBaseIdEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流全链路幂等记录实体。
 *
 * <p>对应数据库表 {@code ydsz_flow_idempotent}，记录工作流操作（发起/推进/驳回等）的幂等状态。
 * 用于防重放、去重、结果缓存，保证同一幂等键在作用域内只执行一次。
 *
 * <p><b>生命周期：</b>
 * <ul>
 *   <li>PROCESSING — 处理中（默认），防并发重复提交</li>
 *   <li>SUCCESS — 成功完成，缓存 result_data 供重复请求直接返回</li>
 *   <li>FAILED — 失败，超过 ttl_at 后自动清理</li>
 * </ul>
 *
 * <p><b>TTL 策略：</b>凭 {@code ttl_at} 字段由定时任务自动清理过期记录，无需逻辑删除。
 *
 * <p><b>表特征：</b>该表无 created_by/updated_by 审计人字段，仅含 id 主键，继承 {@link MpBaseIdEntity}。
 *
 * @author ydsz-team
 * @since 26.10.02
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowIdempotent extends MpBaseEntity<String> {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 幂等作用域（如 workflow.advance / workflow.start / workflow.reject） */
  @TableField("scope")
  private String scope;

  /** 幂等键哈希（SHA-256），唯一约束 */
  @TableField("key_hash")
  private String keyHash;

  /** 幂等键明文（便于排查） */
  @TableField("key_raw")
  private String keyRaw;

  /** 成功结果 JSON（供重复请求直接返回） */
  @TableField("result_data")
  private String resultData;

  /** 重试次数 */
  @TableField("retry_count")
  private Integer retryCount;

  /** 最后一次错误信息 */
  @TableField("error_message")
  private String errorMessage;

  /** 过期时间（自动清理依据） */
  @TableField("ttl_at")
  private LocalDateTime ttlAt;
}
