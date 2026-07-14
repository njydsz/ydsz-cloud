package com.njydsz.pmis.message.server.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.pmis.common.notify.channel.NotifyChannelStrategy;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import com.njydsz.pmis.message.server.channel.MessageChannel;
import com.njydsz.pmis.message.server.channel.NotifyChannelStrategyAdapter;

import lombok.extern.slf4j.Slf4j;

/**
 * 通道桥接自动配置。
 *
 * <p>启动时收集所有 {@link MessageChannel} Bean，为每个在 {@link NotifyChannel} 枚举中
 * 有对应值的通道创建 {@link NotifyChannelStrategyAdapter}，注册为
 * {@link NotifyChannelStrategy} Bean。
 *
 * <p>通道类型映射：
 * <ul>
 *   <li>EMAIL → {@link NotifyChannel#EMAIL}</li>
 *   <li>SMS → {@link NotifyChannel#SMS}</li>
 *   <li>DINGTALK → {@link NotifyChannel#DINGTALK}</li>
 *   <li>WECOM → {@link NotifyChannel#WECOM}</li>
 *   <li>FEISHU → {@link NotifyChannel#FEISHU}</li>
 *   <li>INAPP → {@link NotifyChannel#INSITE}</li>
 * </ul>
 *
 * <p>PUSH / WEBHOOK 等无对应枚举值的通道不会被适配（不影响消息服务内部使用）。
 *
 * <p>注册的适配器以 {@code NotifyChannelStrategy} 接口类型注册，
 * common-notify 的 {@code NotifyServiceImpl} 通过 {@code List<NotifyChannelStrategy>}
 * 自动收集，实现两套体系的统一。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(NotifyChannelStrategy.class)
public class NotifyChannelBridgeConfiguration {

    /** 消息服务通道类型 → common-notify 渠道枚举映射 */
    private static final Map<String, NotifyChannel> CHANNEL_MAP = Map.of(
            "EMAIL", NotifyChannel.EMAIL,
            "SMS", NotifyChannel.SMS,
            "DINGTALK", NotifyChannel.DINGTALK,
            "WECOM", NotifyChannel.WECOM,
            "WECOM_APP", NotifyChannel.WECOM,
            "FEISHU", NotifyChannel.FEISHU,
            "INAPP", NotifyChannel.INSITE
    );

    /**
     * 注册通道适配器列表。
     *
     * <p>每个适配器实现 {@link NotifyChannelStrategy} 接口，
     * common-notify 的 {@code NotifyServiceImpl} 通过 {@code List<NotifyChannelStrategy>}
     * 自动收集这些适配器，从而复用消息服务的通道实现。
     *
     * @param beanFactory Spring Bean 工厂
     * @return 通道适配器列表
     */
    @Bean
    public List<NotifyChannelStrategy> notifyChannelStrategyAdapters(
            ListableBeanFactory beanFactory) {
        Map<String, MessageChannel> channels = beanFactory.getBeansOfType(MessageChannel.class);
        List<NotifyChannelStrategy> adapters = new ArrayList<>();
        for (Map.Entry<String, MessageChannel> entry : channels.entrySet()) {
            MessageChannel channel = entry.getValue();
            String channelType = channel.channelType() == null
                    ? "" : channel.channelType().trim().toUpperCase();
            NotifyChannel notifyChannel = CHANNEL_MAP.get(channelType);
            if (notifyChannel == null) {
                log.debug("[NotifyBridge] 通道 {} 无对应 NotifyChannel 枚举,跳过适配: type={}",
                        entry.getKey(), channelType);
                continue;
            }
            NotifyChannelStrategyAdapter adapter = new NotifyChannelStrategyAdapter(channel, notifyChannel);
            adapters.add(adapter);
            log.info("[NotifyBridge] 通道适配器已注册: bean={} type={} → {}",
                    entry.getKey(), channelType, notifyChannel.getName());
        }
        log.info("[NotifyBridge] 通道桥接完成,共注册 {} 个 NotifyChannelStrategy 适配器", adapters.size());
        return adapters;
    }
}
