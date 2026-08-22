package com.njydsz.message.server.channel.recall;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import com.njydsz.message.domain.vo.MsgLogVO;

/**
 * 撤回通道路由器（P2-F2: 跨通道消息撤回能力扩展）。
 *
 * <p>根据消息日志的通道类型路由到对应的 {@link RecallChannel} 实现。 当没有注册具体通道的撤回实现时，回退到 {@link DefaultRecallChannel}（仅
 * DB 标记）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecallChannelRouter implements InitializingBean {

  private final List<RecallChannel> recallChannels;
  private final DefaultRecallChannel defaultRecallChannel;

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
    log.info("[RecallRouter] 已注册 {} 个通道撤回实现: {}", channelMap.size(), channelMap.keySet());
  }

  /**
   * 路由并执行撤回。
   *
   * @param log 消息日志
   * @return 撤回结果
   */
  public RecallChannel.RecallResult routeAndRecall(MsgLogVO log) {
    if (log == null) {
      return RecallChannel.RecallResult.failed("消息日志为空");
    }
    String channel = log.getChannel();
    RecallChannel handler = channelMap.getOrDefault(channel, defaultRecallChannel);
    log.debug(
        "[RecallRouter] 路由撤回: channel={} handler={}", channel, handler.getClass().getSimpleName());
    return handler.recall(log);
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
