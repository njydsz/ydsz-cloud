package com.njydsz.common.socket.push;

/**
 * 推送投递结果（带端到端状态，ARCH-002）。
 *
 * <p>在 {@link PushResult} 基础上增加 {@link WebSocketDeliveryStatus} 维度，
 * 告知调用方当前结果代表的投递语义：
 *
 * <ul>
 *   <li>{@code success=true, deliveryStatus=TRANSMITTED} → 消息已被集群中间件/本地传输层接收，
 *       但客户端是否真正收到尚不明确（需要客户端 ACK 确认）
 *   <li>{@code success=true, deliveryStatus=QUEUED} → 消息因集群降级被缓存，将异步重投
 *   <li>{@code success=false, deliveryStatus=FAILED} → 消息投递彻底失败
 * </ul>
 *
 * @param pushResult 基础推送结果
 * @param deliveryStatus 投递状态
 * @param deliveryTimestamp 投递状态确定时的时间戳（毫秒）
 * @author ydsz-team
 * @since 26.09.20
 */
public record WebSocketDeliveryResult(
    PushResult pushResult,
    WebSocketDeliveryStatus deliveryStatus,
    long deliveryTimestamp) {

  /**
   * 创建端到端已确认的结果。
   *
   * @param messageId 消息 ID
   * @return 已客户端确认的结果
   */
  public static WebSocketDeliveryResult acknowledged(String messageId) {
    return new WebSocketDeliveryResult(
        PushResult.success(messageId),
        WebSocketDeliveryStatus.CLIENT_ACKNOWLEDGED,
        System.currentTimeMillis());
  }

  /**
   * 创建已传输（集群/本地投递成功但未经客户端确认）的结果。
   *
   * @param messageId 消息 ID
   * @return 已传输的结果
   */
  public static WebSocketDeliveryResult transmitted(String messageId) {
    return new WebSocketDeliveryResult(
        PushResult.success(messageId),
        WebSocketDeliveryStatus.TRANSMITTED,
        System.currentTimeMillis());
  }

  /**
   * 创建已降级入队的结果（集群广播异步投递中）。
   *
   * @param messageId 消息 ID
   * @return 已入队的结果
   */
  public static WebSocketDeliveryResult queued(String messageId) {
    return new WebSocketDeliveryResult(
        PushResult.success(messageId),
        WebSocketDeliveryStatus.QUEUED,
        System.currentTimeMillis());
  }

  /**
   * 创建最终失败结果。
   *
   * @param messageId 消息 ID
   * @param errorCode 错误码
   * @param errorMessage 错误描述
   * @return 失败结果
   */
  public static WebSocketDeliveryResult failed(
      String messageId, String errorCode, String errorMessage) {
    return new WebSocketDeliveryResult(
        PushResult.failure(messageId, errorCode, errorMessage),
        WebSocketDeliveryStatus.FAILED,
        System.currentTimeMillis());
  }

  /**
   * 是否成功传输（CLIENT_ACKNOWLEDGED 或 TRANSMITTED）。
   *
   * @return true 表示消息已被中间件接收或客户端确认
   */
  public boolean isSuccessfullyTransmitted() {
    return deliveryStatus != null && deliveryStatus.isSuccessfullyTransmitted();
  }

  /**
   * 是否为终态（CLIENT_ACKNOWLEDGED 或 FAILED）。
   *
   * @return true 表示投递已到达最终状态
   */
  public boolean isTerminal() {
    return deliveryStatus != null && deliveryStatus.isTerminal();
  }
}
