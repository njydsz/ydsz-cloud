package com.njydsz.common.netty.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;

/**
 * Channel 组管理器。
 *
 * <p>维护所有活跃 Channel 的全局组，支持批量推送/广播。 同时维护业务级 Channel 分组（按 groupKey 分组），支持按分组推送。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class ChannelGroupManager {

  /** 全局 Channel 组 */
  private final ChannelGroup globalGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

  /** 业务级分组（groupKey -> ChannelGroup） */
  private final Map<String, ChannelGroup> businessGroups = new ConcurrentHashMap<>();

  /**
   * 添加 Channel 到全局组。
   *
   * @param channel Netty Channel
   */
  public void add(Channel channel) {
    globalGroup.add(channel);
    log.debug(
        "[Netty-ChannelGroup] Channel 加入全局组: id={}, remote={}",
        channel.id(),
        channel.remoteAddress());
  }

  /**
   * 添加 Channel 到指定业务分组。
   *
   * @param groupKey 分组 key
   * @param channel Netty Channel
   */
  public void addToGroup(String groupKey, Channel channel) {
    businessGroups
        .computeIfAbsent(groupKey, k -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE))
        .add(channel);
    log.debug("[Netty-ChannelGroup] Channel 加入分组: groupKey={}, id={}", groupKey, channel.id());
  }

  /**
   * 从全局组和所有业务分组移除 Channel，并清理空分组。
   *
   * @param channel Netty Channel
   */
  public void remove(Channel channel) {
    globalGroup.remove(channel);
    businessGroups.values().forEach(g -> g.remove(channel));
    // 清理空分组，避免长期运行后积累大量空 ChannelGroup
    businessGroups.entrySet().removeIf(e -> e.getValue().isEmpty());
  }

  /**
   * 向全局所有 Channel 广播消息。
   *
   * @param message 消息
   */
  public void broadcast(Object message) {
    globalGroup.writeAndFlush(message);
  }

  /**
   * 向指定业务分组广播消息。
   *
   * @param groupKey 分组 key
   * @param message 消息
   */
  public void broadcastToGroup(String groupKey, Object message) {
    ChannelGroup group = businessGroups.get(groupKey);
    if (group != null) {
      group.writeAndFlush(message);
    }
  }

  /**
   * 获取全局活跃 Channel 数量。
   *
   * @return 活跃 Channel 数
   */
  public int globalSize() {
    return globalGroup.size();
  }

  /**
   * 获取指定业务分组活跃 Channel 数量。
   *
   * @param groupKey 分组 key
   * @return 活跃 Channel 数
   */
  public int groupSize(String groupKey) {
    ChannelGroup group = businessGroups.get(groupKey);
    return group == null ? 0 : group.size();
  }

  /**
   * 获取所有业务分组 key。
   *
   * @return 分组 key 列表
   */
  public List<String> getGroupKeys() {
    return new ArrayList<>(businessGroups.keySet());
  }
}
