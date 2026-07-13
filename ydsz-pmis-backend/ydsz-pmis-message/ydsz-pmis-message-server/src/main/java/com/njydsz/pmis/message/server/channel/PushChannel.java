package com.njydsz.pmis.message.server.channel.impl;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.server.channel.MessageChannel;
import com.njydsz.pmis.message.server.channel.push.PushProvider;
import com.njydsz.pmis.message.server.config.MessageProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * APP 推送通道门面（替换原 MockPushChannel）。
 *
 * <p>实现 {@link MessageChannel} SPI，内部根据 {@code pmis.message.push.provider}
 * 配置选择实际 {@link PushProvider}（getui / mock），无匹配时降级到 mock。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class PushChannel implements MessageChannel {

    private static final String CHANNEL_TYPE = "PUSH";

    private final List<PushProvider> providers;
    private final MessageProperties messageProperties;

    public PushChannel(List<PushProvider> providers, MessageProperties messageProperties) {
        this.providers = providers != null ? providers : List.of();
        this.messageProperties = messageProperties;
    }

    @Override
    public String channelType() {
        return CHANNEL_TYPE;
    }

    @Override
    public MessageResult send(MessageRequest request) {
        if (request.getReceiver() == null || request.getReceiver().isBlank()) {
            return MessageResult.fail(CHANNEL_TYPE, "推送目标不能为空");
        }
        PushProvider provider = selectProvider();
        MessageResult result = provider.send(request, null);
        log.info("[PushChannel] provider={} status={} target={}",
                provider.providerType(), result.getStatus(), request.getReceiver());
        return result;
    }

    /**
     * P1-10: 批量推送到多个设备（委托给 provider 的原生批量接口）。
     *
     * @param requests 消息请求列表
     * @return 发送结果列表
     */
    public List<MessageResult> batchSend(List<MessageRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        PushProvider provider = selectProvider();
        List<MessageResult> results = provider.batchSend(requests, null);
        log.info("[PushChannel] 批量推送: provider={} count={} success={}",
                provider.providerType(), requests.size(),
                results.stream().filter(MessageResult::isSuccess).count());
        return results;
    }

    /**
     * 根据配置选择 provider，无匹配时降级到 mock。
     *
     * @return 推送服务商
     */
    private PushProvider selectProvider() {
        String target = messageProperties.getPush() != null
                && StringUtils.hasText(messageProperties.getPush().getProvider())
                ? messageProperties.getPush().getProvider() : "mock";
        return providers.stream()
                .filter(p -> target.equalsIgnoreCase(p.providerType()))
                .findFirst()
                .orElseGet(() -> providers.stream()
                        .filter(p -> "mock".equalsIgnoreCase(p.providerType()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "无可用 PUSH provider，请检查 PushProvider Bean 注册")));
    }
}
