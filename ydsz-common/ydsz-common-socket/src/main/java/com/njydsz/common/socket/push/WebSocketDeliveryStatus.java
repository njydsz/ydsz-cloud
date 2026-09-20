package com.njydsz.common.socket.push;

/**
 * 消息投递状态枚举（ARCH-002）。
 *
 * <p>描述推送消息从服务端发出到客户端确认的全生命周期状态，区分"已广播/已本地传输"与"客户端已确认"，
 * 使调用方明确感知推送结果语义。
 *
 * <p>与 {@link PushResult#success()} 的关系：
 * {@code PushResult.success()=true} 表示消息已被集群中间件接收（TRANSMITTED），
 * 但不保证客户端已实际收到。仅当 {@code deliveryStatus=CLIENT_ACKNOWLEDGED} 时代表端到端送达。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public enum WebSocketDeliveryStatus {

  /**
   * 消息已入队/重试缓冲（集群未启用或集群广播失败时的中间状态）。
   *
   * <p>消息会在后续重试周期（{@code RetryFlushTask}）中重新投递，最终可达。
   */
  QUEUED,

  /**
   * 消息已成功广播（集群 Pub/Sub 发布成功）或已本地传输（降级到本节点 STOMP 发送）。
   *
   * <p><b>注意</b>：此状态不保证客户端已收到。客户端可能即将断连、网络可能存在丢包。
   * 对于强可靠性场景，应启用客户端 ACK 机制（{@code ack.enabled=true}）。
   */
  TRANSMITTED,

  /**
   * 客户端已确认收到（仅当 {@code ack.enabled=true} 且客户端实现了 REC 帧回执时可达）。
   *
   * <p>未启用客户端 ACK 时，消息状态停留在 {@link #TRANSMITTED}。
   */
  CLIENT_ACKNOWLEDGED,

  /**
   * 消息投递最终失败（重试耗尽或目标用户永久不可达）。
   *
   * <p>此时消息已移入死信队列（若启用），需干预处理。
   */
  FAILED;

  /**
   * 是否为终态（不再变化）。
   *
   * @return true 表示 CLIENT_ACKNOWLEDGED 或 FAILED
   */
  public boolean isTerminal() {
    return this == CLIENT_ACKNOWLEDGED || this == FAILED;
  }

  /**
   * 是否已成功投递（含 TRANSMITTED 和 CLIENT_ACKNOWLEDGED）。
   *
   * @return true 表示消息已被中间件/本地传输层接收
   */
  public boolean isSuccessfullyTransmitted() {
    return this == TRANSMITTED || this == CLIENT_ACKNOWLEDGED;
  }
}
