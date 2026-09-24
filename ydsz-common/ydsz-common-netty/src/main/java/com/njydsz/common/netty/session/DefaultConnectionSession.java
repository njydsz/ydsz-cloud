package com.njydsz.common.netty.session;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ConnectionSession 的默认实现。
 *
 * <p>基于 Netty Channel 构建，维护状态机、最后交互时间和附件属性。 所有状态变更和 attr 操作均为线程安全。
 *
 * <p>此实现由框架内部创建，业务层不应直接实例化。 通过 {@link SessionRepository#getById(String)} 获取。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class DefaultConnectionSession implements ConnectionSession {

  /** Channel 属性 key 前缀（避免与业务层 attr 冲突） */
  private static final String ATTR_PREFIX = "ydsz.";

  private final String sessionId;
  private final Channel channel;
  private final long connectedTime;

  /** 业务标识（AtomicReference 保证 setBizId 的幂等性） */
  private final AtomicReference<String> bizId = new AtomicReference<>();

  /** Channel 状态（保证状态流转的原子性） */
  private final AtomicReference<ChannelState> state = new AtomicReference<>(ChannelState.CONNECTED);

  /** 最后交互时间 */
  private volatile long lastInteractionTime;

  /** 业务附件属性 */
  private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

  /**
   * 构造默认连接会话。
   *
   * @param channel 底层 Netty Channel
   */
  public DefaultConnectionSession(Channel channel) {
    this.channel = channel;
    this.sessionId = channel.id().asShortText() + "@" + System.currentTimeMillis();
    this.connectedTime = System.currentTimeMillis();
    this.lastInteractionTime = this.connectedTime;
  }

  @Override
  public String getSessionId() {
    return sessionId;
  }

  @Override
  public Channel getChannel() {
    return channel;
  }

  @Override
  public String getBizId() {
    return bizId.get();
  }

  @Override
  public void setBizId(String bizId) {
    if (bizId == null || bizId.isEmpty()) {
      throw new IllegalArgumentException("bizId 不能为空");
    }
    String current = this.bizId.get();
    if (current != null) {
      if (current.equals(bizId)) {
        return; // 幂等：相同值静默忽略
      }
      throw new IllegalStateException("bizId 已设置，不可重复设置: current=" + current + ", new=" + bizId);
    }
    this.bizId.compareAndSet(null, bizId);
  }

  @Override
  public ChannelState getState() {
    return state.get();
  }

  @Override
  public boolean transitionState(ChannelState newState) {
    ChannelState current;
    do {
      current = state.get();
      // CLOSED 是终态，不可再流转（已在 CLOSED 时返回 false）
      if (current == ChannelState.CLOSED) {
        if (newState == ChannelState.CLOSED) {
          return false;
        }
        throw new IllegalStateException("状态已为 CLOSED，无法流转到 " + newState);
      }
      // 相同状态 -> 无变化
      if (current == newState) {
        return false;
      }
    } while (!state.compareAndSet(current, newState));
    log.debug("[Netty-Session] Session {} 状态流转: {} -> {}", sessionId, current, newState);
    return true;
  }

  @Override
  public long getConnectedTime() {
    return connectedTime;
  }

  @Override
  public long getLastInteractionTime() {
    return lastInteractionTime;
  }

  @Override
  public void touch() {
    this.lastInteractionTime = System.currentTimeMillis();
  }

  @Override
  public <T> void setAttr(String key, T value) {
    attributes.put(ATTR_PREFIX + key, value);
  }

  @Override
  // YDIZ-WARN-001 允许保留：会话 Attribute 泛型擦除，调用方显式转型
  @SuppressWarnings("unchecked")
  public <T> T getAttr(String key, T defaultValue) {
    Object value = attributes.get(ATTR_PREFIX + key);
    return value != null ? (T) value : defaultValue;
  }

  @Override
  // YDIZ-WARN-001 允许保留：会话 Attribute 泛型擦除，调用方显式转型
  @SuppressWarnings("unchecked")
  public <T> T removeAttr(String key) {
    Object removed = attributes.remove(ATTR_PREFIX + key);
    return removed != null ? (T) removed : null;
  }

  @Override
  public boolean hasAttr(String key) {
    return attributes.containsKey(ATTR_PREFIX + key);
  }

  @Override
  public ChannelFuture send(Object message) {
    if (!isActive()) {
      throw new IllegalStateException("连接未活跃，无法发送消息: sessionId=" + sessionId + ", state=" + state.get());
    }
    touch();
    return channel.writeAndFlush(message);
  }

  @Override
  public CompletableFuture<Void> sendAsync(Object message) {
    if (!isActive()) {
      throw new IllegalStateException("连接未活跃，无法发送消息: sessionId=" + sessionId + ", state=" + state.get());
    }
    touch();
    CompletableFuture<Void> future = new CompletableFuture<>();
    channel.writeAndFlush(message).addListener(f -> {
      if (f.isSuccess()) {
        future.complete(null);
      } else {
        future.completeExceptionally(
            new RuntimeException("消息发送失败: " + f.cause().getMessage(), f.cause()));
      }
    });
    return future;
  }

  @Override
  public ChannelFuture close() {
    transitionState(ChannelState.DRAINING);
    return channel.close();
  }

  @Override
  public void closeImmediately() {
    transitionState(ChannelState.CLOSED);
    if (channel.isActive()) {
      try {
        channel.close().await(5);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public String toString() {
    return String.format("DefaultConnectionSession{id='%s', bizId='%s', state=%s, channel=%s}",
        sessionId, bizId.get(), state.get(), channel.id().asShortText());
  }
}
