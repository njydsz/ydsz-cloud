package com.njydsz.message.server.service;

import java.time.LocalDateTime;

/**
 * SSE 事件日志条目（供 Last-Event-ID 断线重连回放）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SseEventEntry {

  private final long eventId;
  private final String batchId;
  private final String eventType;
  private final Object data;
  private final LocalDateTime createdAt;

  public SseEventEntry(long eventId, String batchId, String eventType, Object data) {
    this.eventId = eventId;
    this.batchId = batchId;
    this.eventType = eventType;
    this.data = data;
    this.createdAt = LocalDateTime.now();
  }

  public long getEventId() {
    return eventId;
  }

  public String getBatchId() {
    return batchId;
  }

  public String getEventType() {
    return eventType;
  }

  public Object getData() {
    return data;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
