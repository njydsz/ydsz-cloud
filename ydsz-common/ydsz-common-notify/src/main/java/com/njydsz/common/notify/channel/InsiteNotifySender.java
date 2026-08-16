package com.njydsz.common.notify.channel;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.notify.config.NotifyProperties;
import com.njydsz.common.notify.core.NotifySendResult;
import com.njydsz.common.notify.enums.NotifyChannel;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 站内信通知发送器
 *
 * <p>实现 {@link NotifyChannelStrategy} 接口，将站内信存储到 Redis List， 支持前端通过 WebSocket 或轮询拉取。
 *
 * <p>存储结构（Redis List）：
 *
 * <ul>
 *   <li>Key：{@code notify:insite:{userId}}
 *   <li>Value：JSON 格式的 {@link InsiteMessage}
 *   <li>TTL：{@code ydsz.notify.insite.expire-minutes}（默认 1440 分钟 = 24 小时）
 *   <li>容量上限：{@code ydsz.notify.insite.max-queue-size}（默认 10000 条/用户）
 * </ul>
 *
 * <p>当 {@code StringRedisTemplate} 不可用时降级为不操作（返回失败）。
 *
 * <p><b>配置示例（application.yml）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   notify:
 *     insite:
 *       enabled: true
 *       storage-type: redis
 *       max-queue-size: 10000
 *       expire-minutes: 1440
 * }</pre>
 *
 * <p><b>收敛定位</b>：作为 common-notify 内置的 INSITE Provider， 当 message 模块不存在时提供默认的站内信存储能力。 若 message
 * 模块存在，其 {@code InAppChannel} 通过 {@code NotifyChannelBridgeConfiguration} 桥接后自动覆盖本实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "ydsz.notify.insite", name = "enabled", havingValue = "true")
public class InsiteNotifySender implements NotifyChannelStrategy {

  private static final Logger log = LoggerFactory.getLogger(InsiteNotifySender.class);

  /** Redis Key 前缀 */
  private static final String KEY_PREFIX = "notify:insite:";

  private final NotifyProperties.InsiteConfig insiteConfig;
  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  /**
   * 构造站内信发送器
   *
   * @param notifyProperties 通知配置属性
   * @param redisTemplateProvider Redis 模板（可选，缺失时降级为不操作）
   */
  public InsiteNotifySender(
      NotifyProperties notifyProperties,
      ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    this.insiteConfig = notifyProperties.getInsite();
    this.redisTemplateProvider = redisTemplateProvider;
  }

  @Override
  public NotifyChannel getChannel() {
    return NotifyChannel.INSITE;
  }

  /**
   * 发送站内信通知
   *
   * @param receiver 接收者用户 ID
   * @param title 消息标题
   * @param content 消息内容
   * @return 发送结果
   */
  @Override
  public NotifySendResult send(String receiver, String title, String content) {
    if (!isEnabled()) {
      return NotifySendResult.failure("站内信渠道未启用", getChannel().getName());
    }
    if (receiver == null || receiver.isBlank()) {
      return NotifySendResult.failure("接收者 ID 为空", getChannel().getName());
    }
    try {
      StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
      if (redisTemplate == null) {
        return NotifySendResult.failure("Redis 不可用，无法发送站内信", getChannel().getName());
      }

      InsiteMessage message = new InsiteMessage();
      message.setId(UUID.randomUUID().toString());
      message.setTitle(title);
      message.setContent(content);
      message.setTimestamp(System.currentTimeMillis());
      message.setRead(false);

      String key = KEY_PREFIX + receiver;
      String json = YdszJson.toJson(message);

      redisTemplate.opsForList().leftPush(key, json);
      redisTemplate.expire(key, Duration.ofMinutes(insiteConfig.getExpireMinutes()));

      // 控制队列长度，移除最旧的消息
      Long size = redisTemplate.opsForList().size(key);
      if (size != null && size > insiteConfig.getMaxQueueSize()) {
        redisTemplate.opsForList().trim(key, 0, insiteConfig.getMaxQueueSize() - 1);
      }

      log.debug("[InsiteNotifySender] 站内信已存储: userId={}, msgId={}", receiver, message.getId());
      return NotifySendResult.success(message.getId(), getChannel().getName());
    } catch (Exception e) {
      log.error("[InsiteNotifySender] 站内信存储失败: userId={}, error={}", receiver, e.getMessage(), e);
      return NotifySendResult.failure(e.getMessage(), getChannel().getName());
    }
  }

  /**
   * 使用模板发送站内信（模板渲染后的内容作为 content）
   *
   * @param receiver 接收者用户 ID
   * @param templateCode 模板编码（当前版本仅记录，未做模板渲染）
   * @param templateParams 模板参数（序列化到 content 中）
   * @return 发送结果
   */
  @Override
  public NotifySendResult sendTemplate(
      String receiver, String templateCode, Object templateParams) {
    String content = templateParams != null ? templateParams.toString() : "";
    return send(receiver, "模板消息：" + templateCode, content);
  }

  /**
   * 批量发送站内信通知
   *
   * @param receivers 接收者用户 ID 列表
   * @param title 消息标题
   * @param content 消息内容
   * @return 发送结果
   */
  @Override
  public NotifySendResult batchSend(List<String> receivers, String title, String content) {
    if (!isEnabled()) {
      return NotifySendResult.failure("站内信渠道未启用", getChannel().getName());
    }
    if (receivers == null || receivers.isEmpty()) {
      return NotifySendResult.failure("接收者列表为空", getChannel().getName());
    }
    int successCount = 0;
    int failureCount = 0;
    for (String receiver : receivers) {
      NotifySendResult result = send(receiver, title, content);
      if (result.isSuccess()) {
        successCount++;
      } else {
        failureCount++;
      }
    }
    if (failureCount == 0) {
      return NotifySendResult.success("batch:" + successCount, getChannel().getName());
    }
    return NotifySendResult.failure(
        "部分发送失败: 成功" + successCount + "/" + receivers.size(), getChannel().getName());
  }

  /**
   * 判断站内信渠道是否启用
   *
   * @return 启用返回 true，否则返回 false
   */
  @Override
  public boolean isEnabled() {
    return insiteConfig != null && insiteConfig.isEnabled();
  }

  /** 站内信消息体 */
  @Data
  public static class InsiteMessage {
    /** 消息唯一 ID */
    private String id;

    /** 消息标题 */
    private String title;

    /** 消息内容 */
    private String content;

    /** 发送时间戳（毫秒） */
    private long timestamp;

    /** 是否已读 */
    private boolean read;
  }
}
