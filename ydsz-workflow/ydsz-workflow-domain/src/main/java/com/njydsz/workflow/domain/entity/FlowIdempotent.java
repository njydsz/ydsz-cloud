package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工作流幂等记录实体（ydsz_flow_idempotent）。
 *
 * <p>全链路幂等保障：防止 MQ 重复消费、网络超时重试、用户双击提交等场景的操作重复。
 * 幂等键 = scope + keyHash（SHA-256 of scope + keyRaw），状态机：PROCESSING → SUCCESS / FAILED。
 *
 * <p><b>使用约束：</b>
 * <ul>
 *   <li>仅用于非幂等操作的幂等化包装（如 start / advance / reject），天然幂等的查询类操作无需使用</li>
 *   <li>ttlAt 默认 7 天，由定时清理 Job 维护</li>
 *   <li>SUCCESS 状态重复请求直接返回 resultData 缓存，不落库第二次</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("ydsz_flow_idempotent")
public class FlowIdempotent {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 主键 ID（Snowflake） */
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  /** 幂等作用域（如 workflow.advance / workflow.start / workflow.reject） */
  private String scope;

  /** 幂等键哈希（SHA-256），唯一约束 */
  private String keyHash;

  /** 幂等键明文（便于排查） */
  private String keyRaw;

  /** 成功结果 JSON（供重复请求直接返回） */
  private String resultData;

  /** 处理状态：PROCESSING=处理中，SUCCESS=已成功，FAILED=处理失败可重试 */
  private String status;

  /** 重试次数 */
  private Integer retryCount;

  /** 最后一次错误信息 */
  private String errorMessage;

  /** 租户 ID */
  private String tenantId;

  /** 过期时间（自动清理依据） */
  private LocalDateTime ttlAt;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 最后更新时间 */
  private LocalDateTime updatedAt;
}
