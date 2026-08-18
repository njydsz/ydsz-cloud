package com.njydsz.message.domain.enums.core;

/**
 * 消息发送策略枚举。
 *
 * <p>用于统一 {@code /send} 端点的发送模式分发，替代原有的多个独立发送端点：
 *
 * <ul>
 *   <li>{@link #SYNC}：同步发送，阻塞返回供应商结果（原 {@code /send}）
 *   <li>{@link #DIRECT}：直接发送，使用本模块 DTO（原 {@code /sendDirect}）
 *   <li>{@link #ASYNC}：异步发送，先落库 PENDING 再投递 MQ（原 {@code /sendAsync}）
 *   <li>{@link #TRANSACTIONAL}：事务消息，RocketMQ 半消息机制（原 {@code /sendTransactional}）
 *   <li>{@link #BATCH}：批量发送，同步循环限制 100 条/批（原 {@code /batchSend}）
 * </ul>
 *
 * <p>默认值：{@link #SYNC}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum SendStrategyEnum {

  /** 同步发送（阻塞返回供应商结果） */
  SYNC,
  /** 直接发送（使用本模块 DTO，含扩展字段） */
  DIRECT,
  /** 异步发送（先落库 PENDING 再投递 MQ） */
  ASYNC,
  /** 事务消息（RocketMQ 半消息 + 本地事务校验） */
  TRANSACTIONAL,
  /** 批量发送（同步循环，限制 100 条/批） */
  BATCH;

  /**
   * 安全解析发送策略字符串（大小写无关），非法时返回 SYNC。
   *
   * @param value 策略字符串
   * @return 策略枚举，默认 SYNC
   */
  public static SendStrategyEnum parse(String value) {
    if (value == null || value.isBlank()) {
      return SYNC;
    }
    try {
      return SendStrategyEnum.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return SYNC;
    }
  }
}
