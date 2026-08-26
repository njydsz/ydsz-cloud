package com.njydsz.message.domain.enums.core;

/**
 * 消息轨迹节点类型枚举。
 *
 * <p>定义消息从接入到投递全链路的关键节点类型，用于轨迹记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum MsgTraceNodeEnum {

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
