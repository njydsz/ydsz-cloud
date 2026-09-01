package com.njydsz.message.server.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.notify.channel.NotifyChannelStrategy;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.message.server.channel.MessageChannel;
import com.njydsz.message.server.channel.NotifyChannelStrategyAdapter;

/**
 * 通道桥接自动配置。
 *
 * <p>启动时收集所有 {@link MessageChannel} Bean，为每个在 {@link NotifyChannel} 枚举中 有对应值的通道创建 {@link
 * NotifyChannelStrategyAdapter}，注册为 {@link NotifyChannelStrategy} Bean。
 *
 * <p><b>收敛定位</b>：message 模块是 common-notify 的通道 Provider， 业务模块不应直接使用 message 模块的 {@code
 * NotificationClient} Feign 或 {@code MessageChannel}，而应通过 {@code NotifyHelper} 发送通知。 详见 {@code
 * docs/module-review/ADR-001-notify-message-convergence.md}。
 *
 * <p>通道类型映射：
 *
 * <ul>
 *   <li>EMAIL → {@link NotifyChannel#EMAIL}
 *   <li>SMS → {@link NotifyChannel#SMS}
 *   <li>DINGTALK → {@link NotifyChannel#DINGTALK}
 *   <li>WECOM → {@link NotifyChannel#WECOM}
 *   <li>WECOM_APP → {@link NotifyChannel#WECOM}
 *   <li>FEISHU → {@link NotifyChannel#FEISHU}
 *   <li>INAPP → {@link NotifyChannel#INSITE}
 * </ul>
 *
 * <p>PUSH / WEBHOOK 等无对应枚举值的通道不会被适配（不影响消息服务内部使用）。
 *
 * <p>注册的适配器以 {@code NotifyChannelStrategy} 接口类型注册， common-notify 的 {@code NotifyServiceImpl} 通过
 * {@code List<NotifyChannelStrategy>} 自动收集，实现两套体系的统一。
 *
 * <p><b>实现说明</b>：使用 {@link ConfigurableListableBeanFactory#registerSingleton} 将每个适配器注册为独立的 {@link
 * NotifyChannelStrategy} Bean， 而非返回 {@code List<NotifyChannelStrategy>}（后者只注册 List 本身为一个 Bean，
 * common-notify 的 {@code List<NotifyChannelStrategy>} 注入无法收集到 List 内的元素）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(NotifyChannelStrategy.class)
public class NotifyChannelBridgeConfiguration implements InitializingBean {

  /** 消息服务通道类型 → common-notify 渠道枚举映射 */
  private static final Map<String, NotifyChannel> CHANNEL_MAP =
      Map.of(
          "EMAIL", NotifyChannel.EMAIL,
          "SMS", NotifyChannel.SMS,
          "DINGTALK", NotifyChannel.DINGTALK,
          "WECOM", NotifyChannel.WECOM,
          "WECOM_APP", NotifyChannel.WECOM,
          "FEISHU", NotifyChannel.FEISHU,
          "INAPP", NotifyChannel.INSITE);

  private final ListableBeanFactory beanFactory;

  /**
   * 构造通道桥接配置。
   *
   * @param beanFactory Spring Bean 工厂（用于获取所有 MessageChannel Bean 实例）
   */
  public NotifyChannelBridgeConfiguration(ListableBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  public void afterPropertiesSet() {
    if (!(beanFactory instanceof ConfigurableListableBeanFactory configurableFactory)) {
      log.warn(
          "[NotifyBridge] BeanFactory 不是 ConfigurableListableBeanFactory,跳过通道桥接: {}",
          beanFactory.getClass().getName());
      return;
    }

    Map<String, MessageChannel> channels = beanFactory.getBeansOfType(MessageChannel.class);
    List<NotifyChannelStrategy> registered = new ArrayList<>();

    for (Map.Entry<String, MessageChannel> entry : channels.entrySet()) {
      MessageChannel channel = entry.getValue();
      String channelType =
          channel.channelType() == null ? "" : channel.channelType().trim().toUpperCase();
      NotifyChannel notifyChannel = CHANNEL_MAP.get(channelType);
      if (notifyChannel == null) {
        log.debug(
            "[NotifyBridge] 通道 {} 无对应 NotifyChannel 枚举,跳过适配: type={}", entry.getKey(), channelType);
        continue;
      }
      NotifyChannelStrategyAdapter adapter =
          new NotifyChannelStrategyAdapter(channel, notifyChannel);
      String beanName = "notifyAdapter_" + channelType.toLowerCase() + "_" + entry.getKey();
      configurableFactory.registerSingleton(beanName, adapter);
      registered.add(adapter);
      log.info(
          "[NotifyBridge] 通道适配器已注册: bean={} type={} → {}",
          beanName,
          channelType,
          notifyChannel.getName());
    }
    log.info("[NotifyBridge] 通道桥接完成,共注册 {} 个 NotifyChannelStrategy 适配器", registered.size());
  }
}
