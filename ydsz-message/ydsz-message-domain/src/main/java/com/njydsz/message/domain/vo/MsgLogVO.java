package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.njydsz.common.json.annotation.JsonView;
import com.njydsz.common.safe.sensitive.SensitiveData;
import com.njydsz.common.safe.sensitive.SensitiveType;
import lombok.Data;

/**
 * 消息发送日志视图对象（VO）。
 *
 * <p>用于 Controller 层返回消息发送日志的完整信息，包含通道、模板、发送状态、 重试信息、灰度标记、回执状态及成本等，支撑消息全链路追踪与运维排查。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class MsgLogVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 日志唯一标识（主键） */
  @JsonView(MsgLogViews.Summary.class)
  private String id;

  /** 发送通道（SMS/EMAIL/WEBHOOK/WECHAT/INSITE） */
  @JsonView(MsgLogViews.Summary.class)
  private String channel;

  /** 业务类型 */
  @JsonView(MsgLogViews.Summary.class)
  private String bizType;

  /** 业务 ID */
  @JsonView(MsgLogViews.Summary.class)
  private String bizId;

  /** 接收人标识（手机号/邮箱/openid） */
  @SensitiveData(SensitiveType.PHONE)
  @JsonView(MsgLogViews.Summary.class)
  private String receiver;

  /** 模板编码 */
  @JsonView(MsgLogViews.Summary.class)
  private String templateCode;

  /** 模板参数 JSON */
  @JsonView(MsgLogViews.Detail.class)
  private String templateParams;

  /** 实际发送内容 */
  @JsonView(MsgLogViews.Detail.class)
  private String content;

  /** 发送状态（PENDING/SENDING/SUCCESS/FAILED/SKIPPED） */
  @JsonView(MsgLogViews.Summary.class)
  private String status;

  /** 错误信息 */
  @JsonView(MsgLogViews.Detail.class)
  private String errorMessage;

  /** 优先级（LOW/NORMAL/HIGH/URGENT） */
  @JsonView(MsgLogViews.Summary.class)
  private String priority;

  /** 发送人 ID */
  @JsonView(MsgLogViews.Detail.class)
  private String senderId;

  /** 消息分组 */
  @JsonView(MsgLogViews.Detail.class)
  private String messageGroup;

  /** 批次 ID */
  @JsonView(MsgLogViews.Summary.class)
  private String batchId;

  /** 路由规则 ID */
  @JsonView(MsgLogViews.Detail.class)
  private String routeRuleId;

  /** 灰度标记（0=主版本，1=灰度版本） */
  @JsonView(MsgLogViews.Detail.class)
  private Integer canary;

  /** 灰度分桶键 */
  @JsonView(MsgLogViews.Detail.class)
  private String canaryKey;

  /** 去重键 */
  @JsonView(MsgLogViews.Detail.class)
  private String dedupKey;

  /** 撤回状态 */
  @JsonView(MsgLogViews.Detail.class)
  private String recallStatus;

  /** 撤回时间 */
  @JsonView(MsgLogViews.Detail.class)
  private LocalDateTime recallAt;

  /** 回执状态（PENDING/DELIVERED/READ/FAILED） */
  @JsonView(MsgLogViews.Summary.class)
  private String receiptStatus;

  /** 回执时间 */
  @JsonView(MsgLogViews.Detail.class)
  private LocalDateTime receiptAt;

  /** 重试次数 */
  @JsonView(MsgLogViews.Detail.class)
  private Integer retryCount;

  /** 下次重试时间 */
  @JsonView(MsgLogViews.Detail.class)
  private LocalDateTime nextRetryAt;

  /** 供应商追踪 ID */
  @JsonView(MsgLogViews.Detail.class)
  private String providerTraceId;

  /** 发送耗时（毫秒） */
  @JsonView(MsgLogViews.Summary.class)
  private Long costMs;

  /** 发送成本（元） */
  @JsonView(MsgLogViews.Summary.class)
  private BigDecimal cost;

  /** 链路追踪 ID */
  @JsonView(MsgLogViews.Summary.class)
  private String traceId;

  /** 租户 ID */
  @JsonView(MsgLogViews.Detail.class)
  private String tenantId;

  /** 消息 ID */
  @JsonView(MsgLogViews.Summary.class)
  private String msgId;

  /** MQ Topic */
  @JsonView(MsgLogViews.Detail.class)
  private String topic;

  /** MQ 重消费次数 */
  @JsonView(MsgLogViews.Detail.class)
  private Integer reconsumeTimes;

  /** 父消息 ID（聚合/拆分场景） */
  @JsonView(MsgLogViews.Detail.class)
  private String parentMsgId;

  /** 计划发送时间 */
  @JsonView(MsgLogViews.Detail.class)
  private LocalDateTime scheduledAt;

  /** 创建人 */
  @JsonView(MsgLogViews.Detail.class)
  private String createdBy;

  /** 创建时间 */
  @JsonView(MsgLogViews.Summary.class)
  private LocalDateTime createdAt;

  /** 更新人 */
  @JsonView(MsgLogViews.Detail.class)
  private String updatedBy;

  /** 更新时间 */
  @JsonView(MsgLogViews.Detail.class)
  private LocalDateTime updatedAt;
}
