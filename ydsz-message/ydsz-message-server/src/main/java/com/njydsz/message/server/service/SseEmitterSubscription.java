package com.njydsz.message.server.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 订阅信息（封装 SseEmitter 与订阅上下文）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SseEmitterSubscription {

  private final String subscriptionId;
  private final String batchId;
  private final SseEmitter emitter;
  private final String lastEventId;
  private final long connectedAt;

  public SseEmitterSubscription(
      String subscriptionId, String batchId, SseEmitter emitter, String lastEventId) {
    this.subscriptionId = subscriptionId;
    this.batchId = batchId;
    this.emitter = emitter;
    this.lastEventId = lastEventId;
    this.connectedAt = System.currentTimeMillis();
  }

  public String getSubscriptionId() {
    return subscriptionId;
  }

  public String getBatchId() {
    return batchId;
  }

  public SseEmitter getEmitter() {
    return emitter;
  }

  public String getLastEventId() {
    return lastEventId;
  }

  public long getConnectedAt() {
    return connectedAt;
  }
}
