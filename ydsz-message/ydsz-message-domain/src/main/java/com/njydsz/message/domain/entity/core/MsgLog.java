package com.njydsz.message.domain.entity.core;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;
import com.njydsz.common.safe.sensitive.SensitiveData;
import com.njydsz.common.safe.sensitive.SensitiveType;

/**
 * 消息发送日志实体 — 全通道发送全量记录的事实表
 *
 * <p>对应数据库表 {@code ydsz_msg_log}，是消息中心的核心事实表。每条消息从创建到最终送达 （或失败/死信）的完整生命周期记录均存储在此表中，支持优先级排队、消息聚合、撤回、
 * 回执追踪、渠道路由、灰度发布、重试调度等高级能力。
 *
 * <p><b>核心字段分组：</b>
 *
 * <ul>
 *   <li><b>标识</b>：{@code msgId}（雪花算法全局唯一）、{@code traceId}（链路追踪）
 *   <li><b>接收人</b>：{@code receiverUserId} / {@code receiverPhone} / {@code receiverEmail}（脱敏存储）
 *   <li><b>渠道与模板</b>：{@code channel}（8 通道枚举）、{@code templateCode}、{@code templateVersion}
 *   <li><b>状态</b>：{@code status}（{@link com.njydsz.message.domain.enums.core.MessageStatusEnum}）、
 *       {@code receiptStatus}（{@link com.njydsz.message.domain.enums.receipt.ReceiptStatusEnum}）、
 *       {@code recallStatus}（{@link com.njydsz.message.domain.enums.receipt.RecallStatusEnum}）
 *   <li><b>重试</b>：{@code retryCount}（已重试次数）、{@code maxRetryCount}（最大重试次数）、 {@code
 *       nextRetryAt}（下次重试时间，指数退避 + 随机抖动）
 *   <li><b>优先级</b>：{@code priority}（{@link
 *       com.njydsz.message.domain.enums.core.MessagePriorityEnum}）
 *   <li><b>灰度</b>：{@code canaryBucket}（灰度桶标识，命中灰度规则时填充）
 * </ul>
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>唯一索引 {@code uk_msg_id}（{@code msg_id}）— 幂等去重
 *   <li>普通索引 {@code idx_user_status}（{@code receiver_user_id}, {@code status}）— 用户消息列表
 *   <li>普通索引 {@code idx_send_at}（{@code send_at}）— 时间范围查询
 *   <li>普通索引 {@code idx_retry}（{@code status}, {@code next_retry_at}）— 重试扫描
 * </ul>
 *
 * <p><b>多租户：</b>继承 {@link MpBaseEntity} 的 {@code tenantId} 字段，由 MyBatis 拦截器自动注入。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.domain.enums.core.MessageStatusEnum 消息状态枚举
 * @see com.njydsz.message.domain.enums.core.MessageChannelEnum 消息通道枚举
 * @see com.njydsz.message.domain.enums.core.MessagePriorityEnum 消息优先级枚举
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_log")
public class MsgLog extends MpBaseIdEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 发送通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WECOM/FEISHU */
  private String channel;

  /** 业务类型 */
  private String bizType;

  /** 业务单据 ID */
  private String bizId;

  /** 接收人（API 响应自动脱敏：手机号/邮箱/用户 ID 智能识别，落库保留原值） */
  @SensitiveData(SensitiveType.CUSTOM)
  private String receiver;

  /** 模板编码 */
  private String templateCode;

  /** 模板参数 JSON */
  private String templateParams;

  /** 发送内容(渲染后) */
  private String content;

  /** 发送状态: PENDING/SENDING/SUCCESS/FAILED/RETRY/DEAD/RECALLED */
  private String status;

  /** 错误信息 */
  private String errorMessage;

  /** 发送优先级: LOW/NORMAL/HIGH/URGENT(影响排队与并发) */
  private String priority;

  /** 触发发送的用户 ID(系统发送为 SYSTEM) */
  private String senderId;

  /** 聚合组(同组消息可合并为摘要发送) */
  private String messageGroup;

  /** 聚合批次 ID(关联 ydsz_msg_aggregate.id) */
  private String batchId;

  /** 命中的路由规则 ID(关联 ydsz_msg_route_rule.id) */
  private String routeRuleId;

  /** 是否灰度命中: 0 正式 / 1 灰度 */
  private Integer canary;

  /** P1-6: 灰度实验键（命中时记录原始 canaryKey,用于 A/B 报表分组;未命中为 null） */
  private String canaryKey;

  /** 幂等去重键(用于消费端幂等,Redis SET NX EX) */
  private String dedupKey;

  /** 撤回状态: NONE 未撤回 / RECALLED 已撤回 */
  private String recallStatus;

  /** 撤回时间 */
  private LocalDateTime recallAt;

  /** 回执状态: NONE/DELIVERED/READ/CLICKED/FAILED */
  private String receiptStatus;

  /** 回执到达时间 */
  private LocalDateTime receiptAt;

  /** 已重试次数 */
  private Integer retryCount;

  /** 下次重试时间(退避调度) */
  private LocalDateTime nextRetryAt;

  /** 三方服务商回执 ID */
  private String providerTraceId;

  /** 发送耗时(毫秒) */
  private Long costMs;

  /** P2-4: 发送成本(元),按通道单价计算,SMS/EMAIL/PUSH 有成本,IM/INAPP 免费 */
  private BigDecimal cost;

  /** 系统链路追踪 ID */
  private String traceId;

  /** RocketMQ 消息 ID */
  private String msgId;

  /** RocketMQ Topic(DLQ 消息填充原 Topic) */
  private String topic;

  /** RocketMQ 重试次数 */
  private Integer reconsumeTimes;

  /** P2-6: 父消息 ID(级联发送时自动填充,用于追溯级联关系) */
  private String parentMsgId;

  /** P0-3: 定时发送时间(非空时 status=SCHEDULED, 到期后由调度器触发发送) */
  private LocalDateTime scheduledAt;
}
