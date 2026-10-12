package com.njydsz.message.server.channel.recall;


import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.locales.util.I18n;
import com.njydsz.message.domain.vo.MsgLogVO;

/**
 * 撤回通道路由器（P2-F2: 跨通道消息撤回能力扩展）。
 *
 * <p>根据消息日志的通道类型路由到对应的 {@link RecallChannel} 实现。 当没有注册具体通道的撤回实现时，回退到 {@link DefaultRecallChannel}（仅
 * DB 标记）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
public class RecallChannelRouter implements InitializingBean {

  private final List<RecallChannel> recallChannels;

  /**
   * 默认撤回通道（DB 标记模式）。
   *
   * <p>使用 ObjectProvider 延迟获取，Bean 不存在时降级为内部新建实例，避免启动失败。
   */
  private final DefaultRecallChannel defaultRecallChannel;

  /**
   * 手写构造器（替代 @RequiredArgsConstructor），将 DefaultRecallChannel 改为 ObjectProvider 注入 + null 保护，
   * 避免因缺少 DefaultRecallChannel Bean 导致启动失败。
   *
   * @param recallChannels 撤回通道实现列表
   * @param defaultRecallChannelProvider 默认撤回通道（可选）
   */
  public RecallChannelRouter(
      List<RecallChannel> recallChannels,
      ObjectProvider<DefaultRecallChannel> defaultRecallChannelProvider) {
    this.recallChannels = recallChannels;
    // ObjectProvider 延迟获取，不存在时降级为内部新建实例
    DefaultRecallChannel provided = defaultRecallChannelProvider.getIfAvailable();
    this.defaultRecallChannel = provided != null ? provided : new DefaultRecallChannel();
  }

  /** 通道类型 → 撤回实现 */
  private final Map<String, RecallChannel> channelMap = new ConcurrentHashMap<>();

  @Override
  public void afterPropertiesSet() {
    for (RecallChannel ch : recallChannels) {
      if ("DEFAULT".equals(ch.channelType())) {
        continue; // 默认实现不注册到 map
      }
      RecallChannel existing = channelMap.putIfAbsent(ch.channelType(), ch);
      if (existing != null) {
        log.warn(
            "[RecallRouter] 通道撤回实现重复注册,已忽略: channel={} existing={} ignored={}",
            ch.channelType(),
            existing.getClass().getSimpleName(),
            ch.getClass().getSimpleName());
      }
    }
    log.info(I18n.message("message.log.other.RecallRouter_{}_{}.ae6fe4"), channelMap.size(), channelMap.keySet());
  }

  /**
   * 路由并执行撤回。
   *
   * @param msgLog 消息日志
   * @return 撤回结果
   */
  public RecallChannel.RecallResult routeAndRecall(MsgLogVO msgLog) {
    if (msgLog == null) {
      return RecallChannel.RecallResult.failed("消息日志为空");
    }
    String channel = msgLog.getChannel();
    RecallChannel handler = channelMap.getOrDefault(channel, defaultRecallChannel);
    log.debug(
        "[RecallRouter] 路由撤回: channel={} handler={}", channel, handler.getClass().getSimpleName());
    return handler.recall(msgLog);
  }

  /**
   * 判断指定通道是否支持平台 API 撤回。
   *
   * @param channel 通道类型
   * @return true 表示有具体实现（非默认）
   */
  public boolean supportsPlatformRecall(String channel) {
    return channel != null && channelMap.containsKey(channel);
  }
}
