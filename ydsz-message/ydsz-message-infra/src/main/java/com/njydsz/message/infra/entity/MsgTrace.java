package com.njydsz.message.infra.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * 消息轨迹记录表: 记录消息从接入到投递全链路的每个关键节点。
 *
 * <p>P0-2: 端到端消息追踪能力，支撑消息全生命周期可视化。 每条消息在每个关键节点（接收、校验、路由、渲染、投递、回执等）产生一条轨迹记录， 通过 msgId
 * 关联，按时间顺序串联形成完整链路。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_trace")
public class MsgTrace extends MpBaseIdEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 消息 ID（关联 ydsz_msg_log.msg_id） */
  private String msgId;

  /** 链路追踪 ID（关联 ydsz_msg_log.trace_id，用于跨服务链路串联） */
  private String traceId;

  /** 轨迹节点类型 */
  private String node;

  /** 节点状态: SUCCESS / FAILED / SKIPPED / PENDING */
  private String status;

  /** 通道: SMS/EMAIL/PUSH/...（节点关联的通道，部分节点如 RECEIVED 无通道则为 null） */
  private String channel;

  /** 接收人（脱敏后的） */
  private String receiver;

  /** 业务类型 */
  private String bizType;

  /** 业务单据 ID */
  private String bizId;

  /** 模板编码 */
  private String templateCode;

  /** 节点耗时（毫秒） */
  private Long costMs;

  /** 节点描述 / 错误信息 */
  private String message;

  /** 扩展信息 JSON（节点附加数据，如路由规则 ID、降级链、灰度配置等） */
  private String extra;

  /** 节点发生时间 */
  private LocalDateTime eventAt;

  /** 轨迹节点类型枚举。 */
  public enum Node {
    /** 消息接收 */
    RECEIVED,
    /** 通道校验 */
    CHANNEL_CHECK,
    /** 路由匹配 */
    ROUTE_MATCHED,
    /** 灰度命中 */
    CANARY_HIT,
    /** 订阅校验 */
    SUBSCRIPTION_CHECK,
    /** 偏好校验（DND等） */
    PREFERENCE_CHECK,
    /** 去重检查 */
    DEDUP_CHECK,
    /** 限流检查 */
    RATE_LIMIT_CHECK,
    /** 模板加载 */
    TEMPLATE_LOADED,
    /** 模板渲染 */
    TEMPLATE_RENDERED,
    /** 敏感词过滤 */
    SENSITIVE_FILTERED,
    /** 消息落库 */
    PERSISTED,
    /** 定时消息调度 */
    SCHEDULED,
    /** 聚合加入 */
    AGGREGATED,
    /** 通道分发开始 */
    DISPATCH_START,
    /** 通道分发成功 */
    DISPATCH_SUCCESS,
    /** 通道降级 */
    FALLBACK,
    /** 通道重试 */
    RETRY,
    /** 发送失败（终态） */
    SEND_FAILED,
    /** 回执接收 */
    RECEIPT_RECEIVED,
    /** 消息撤回 */
    RECALLED,
    /** 级联发送 */
    CASCADE_SENT
  }
}
