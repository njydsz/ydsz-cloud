package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

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
 * @author ydsz-team
 * @since 26.10.02
 */
@Data
@TableName("ydsz_flow_idempotent")
public class FlowIdempotent {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 主键 ID（Snowflake） */
  @TableId(value = "id", type = IdType.ASSIGN_ID)
  private String id;

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

  /** 处理状态（PROCESSING / SUCCESS / FAILED） */
  @TableField("status")
  private String status;

  /** 重试次数 */
  @TableField("retry_count")
  private Integer retryCount;

  /** 最后一次错误信息 */
  @TableField("error_message")
  private String errorMessage;

  /** 租户 ID */
  @TableField("tenant_id")
  private String tenantId;

  /** 创建时间 */
  @TableField("created_at")
  private LocalDateTime createdAt;

  /** 更新时间 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;

  /** 过期时间（自动清理依据） */
  @TableField("ttl_at")
  private LocalDateTime ttlAt;
}
