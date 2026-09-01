package com.njydsz.message.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 消息发送日志 DTO（命令请求参数）。
 *
 * <p>用于 Repository 层 CUD 操作的统一入参，不区分 Create / Update：
 * <ul>
 *   <li>创建场景：{@code id} 字段不传</li>
 *   <li>更新场景：传入 {@code id}</li>
 * </ul>
 *
 * <p>遵循云顶编码规范第 34 节：domain 层 DTO 不区分 Create/Update，
 * 通过 infra 层 Converter 的 {@code dtoToEntity}（ignore id，创建场景）与
 * {@code dtoToEntityWithId}（保留 id，更新场景）区分行为。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MsgLogDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 日志唯一标识（主键，更新时传入） */
  private String id;

  /** 发送通道（SMS/EMAIL/WEBHOOK/WECHAT/INSITE） */
  private String channel;

  /** 业务类型 */
  private String bizType;

  /** 业务 ID */
  private String bizId;

  /** 接收人标识（手机号/邮箱/openid） */
  private String receiver;

  /** 模板编码 */
  private String templateCode;

  /** 模板参数 JSON */
  private String templateParams;

  /** 实际发送内容 */
  private String content;

  /** 发送状态（PENDING/SENDING/SUCCESS/FAILED/SKIPPED） */
  private String status;

  /** 错误信息 */
  private String errorMessage;

  /** 优先级（LOW/NORMAL/HIGH/URGENT） */
  private String priority;

  /** 发送人 ID */
  private String senderId;

  /** 消息分组 */
  private String messageGroup;

  /** 批次 ID */
  private String batchId;

  /** 路由规则 ID */
  private String routeRuleId;

  /** 灰度标记（0=主版本，1=灰度版本） */
  private Integer canary;

  /** 灰度分桶键 */
  private String canaryKey;

  /** 去重键 */
  private String dedupKey;

  /** 撤回状态 */
  private String recallStatus;

  /** 撤回时间 */
  private LocalDateTime recallAt;

  /** 回执状态（PENDING/DELIVERED/READ/FAILED） */
  private String receiptStatus;

  /** 回执时间 */
  private LocalDateTime receiptAt;

  /** 重试次数 */
  private Integer retryCount;

  /** 下次重试时间 */
  private LocalDateTime nextRetryAt;

  /** 供应商追踪 ID */
  private String providerTraceId;

  /** 发送耗时（毫秒） */
  private Long costMs;

  /** 发送成本（元） */
  private BigDecimal cost;

  /** 链路追踪 ID */
  private String traceId;

  /** 消息 ID */
  private String msgId;

  /** MQ Topic */
  private String topic;

  /** MQ 重消费次数 */
  private Integer reconsumeTimes;

  /** 父消息 ID（聚合/拆分场景） */
  private String parentMsgId;

  /** 计划发送时间 */
  private LocalDateTime scheduledAt;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 租户 ID */
  private String tenantId;

  /** 删除标识 */
  private Boolean deleted;
}
