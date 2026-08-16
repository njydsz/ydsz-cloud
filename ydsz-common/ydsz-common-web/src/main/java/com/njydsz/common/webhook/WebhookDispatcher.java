package com.njydsz.common.webhook;

import java.util.Map;

/**
 * Webhook 统一投递器接口。
 *
 * <p>提供 Webhook 订阅管理（注册 / 注销）与事件投递（HTTP POST + HMAC 签名 + 重试）能力。 各业务模块（message / workflow / project
 * 等）通过此接口统一投递 Webhook 事件， 避免在每个模块中重复实现 HTTP 投递、签名、重试逻辑。
 *
 * <p>实现方需保证：
 *
 * <ul>
 *   <li><b>HMAC-SHA256 签名</b>：投递时在 HTTP Header 中附带 {@code X-Webhook-Signature}， 值为 {@code
 *       HMAC-SHA256(payload, secret)} 的 Base64 编码
 *   <li><b>重试策略</b>：投递失败时按指数退避重试（默认 3 次）
 *   <li><b>事件过滤</b>：仅向订阅了对应 {@code eventType} 的 URL 投递
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface WebhookDispatcher {

  /**
   * 注册 Webhook 订阅。
   *
   * @param subscription 订阅信息
   */
  void register(WebhookSubscription subscription);

  /**
   * 注销 Webhook 订阅。
   *
   * @param subscriptionId 订阅唯一标识
   */
  void unregister(String subscriptionId);

  /**
   * 投递事件到所有匹配的订阅 URL。
   *
   * <p>投递逻辑：
   *
   * <ol>
   *   <li>筛选 {@code enabled=true} 且 {@code eventTypes} 包含 {@code eventType} 的订阅
   *   <li>对每个匹配的订阅，将 {@code payload} 序列化为 JSON 后 POST 到 {@code callbackUrl}
   *   <li>在 HTTP Header 中附带 {@code X-Webhook-Signature}（HMAC-SHA256 签名）
   *   <li>投递失败时按指数退避重试（默认 3 次），最终失败记录日志
   * </ol>
   *
   * @param eventType 事件类型（如 {@code MESSAGE_SENT}）
   * @param payload 事件负载（Map 结构，自动序列化为 JSON）
   */
  void dispatch(String eventType, Map<String, Object> payload);
}
