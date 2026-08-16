package com.njydsz.message.server.config;

import com.njydsz.message.domain.constant.MessageConstants;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * P1-7: 重试策略解析器。
 *
 * <p>根据通道解析生效的重试策略（{@link MessageProperties.RetryPolicy}）， 替代原先硬编码的 {@link
 * MessageConstants#MAX_RETRY_COUNT} 与 {@link MessageConstants#RETRY_BASE_BACKOFF_MS}。
 *
 * <p>解析优先级：
 *
 * <ol>
 *   <li>{@code ydsz.message.channel-retry-policies.{CHANNEL}} 通道级覆盖
 *   <li>{@code ydsz.message.default-retry-policy} 全局默认
 *   <li>代码兜底默认值（maxRetryCount=3, baseBackoffMs=2000, multiplier=2.0, maxBackoffMs=60000）
 * </ol>
 *
 * <p>退避公式：{@code backoff = min(baseBackoffMs * backoffMultiplier^retryCount, maxBackoffMs)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryStrategyResolver {

  private final MessageProperties messageProperties;

  /**
   * 解析指定通道的重试策略。
   *
   * @param channel 通道类型（大小写无关），为空时返回全局默认
   * @return 生效的重试策略（永不返回 null）
   */
  public MessageProperties.RetryPolicy resolve(String channel) {
    MessageProperties.RetryPolicy def = messageProperties.getDefaultRetryPolicy();
    if (def == null) {
      def = new MessageProperties.RetryPolicy();
    }
    if (channel == null || channel.isBlank()) {
      return def;
    }
    Map<String, MessageProperties.RetryPolicy> map = messageProperties.getChannelRetryPolicies();
    if (map == null || map.isEmpty()) {
      return def;
    }
    MessageProperties.RetryPolicy override = map.get(channel.trim().toUpperCase());
    return override != null ? override : def;
  }

  /**
   * 判断是否已达最大重试次数。
   *
   * @param retryCount 当前重试次数（从 0 起）
   * @param channel 通道
   * @return true 表示已达上限，应转死信/失败
   */
  public boolean isMaxRetriesReached(int retryCount, String channel) {
    return retryCount >= resolve(channel).getMaxRetryCount();
  }

  /**
   * 计算下一次重试时间（指数退避 + 上限封顶）。
   *
   * @param retryCount 当前重试次数（即将进入第 retryCount+1 次重试）
   * @param channel 通道
   * @return 下次重试时间
   */
  public LocalDateTime calcNextRetryAt(int retryCount, String channel) {
    return LocalDateTime.now().plusNanos(calcBackoffMs(retryCount, channel) * 1_000_000L);
  }

  /**
   * 计算退避毫秒数：{@code min(base * multiplier^retryCount, maxBackoffMs)}。
   *
   * @param retryCount 当前重试次数
   * @param channel 通道
   * @return 退避毫秒
   */
  public long calcBackoffMs(int retryCount, String channel) {
    MessageProperties.RetryPolicy p = resolve(channel);
    int exp = Math.max(retryCount, 0);
    double raw = p.getBaseBackoffMs() * Math.pow(p.getBackoffMultiplier(), exp);
    long backoff = (long) raw;
    return Math.min(backoff, p.getMaxBackoffMs());
  }
}
