package com.njydsz.pmis.message.channel;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.config.MessageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息通道路由器。
 *
 * <p>启动时通过 {@link ApplicationContext#getBeansOfType(Class)} 收集所有
 * {@link MessageChannel} Bean，按 {@link MessageChannel#channelType()} 大写形式
 * 注册到内部缓存，供 {@link #route(String)} 与 {@link #dispatch(MessageRequest)} 使用。
 *
 * <p>通道开关由 {@code pmis.message.channel-enabled.*} 配置控制，
 * 通过 {@link MessageProperties#getChannelEnabled()} 读取。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelRouter {

    /** Spring 上下文，用于收集通道 Bean */
    private final ApplicationContext applicationContext;

    /** 消息配置，用于读取通道开关 */
    private final MessageProperties messageProperties;

    /** 通道缓存：channelType(大写) -> MessageChannel */
    private final Map<String, MessageChannel> channelCache = new HashMap<>();

    /**
     * 收集所有 MessageChannel Bean 并按通道类型注册。
     */
    @PostConstruct
    public void initChannels() {
        Map<String, MessageChannel> beans = applicationContext.getBeansOfType(MessageChannel.class);
        for (MessageChannel channel : beans.values()) {
            String type = channel.channelType() == null ? "" : channel.channelType().trim().toUpperCase();
            if (type.isEmpty()) {
                log.warn("[ChannelRouter] 跳过 channelType 为空的通道: {}", channel.getClass().getName());
                continue;
            }
            channelCache.put(type, channel);
        }
        log.info("[ChannelRouter] 已注册 {} 个消息通道: {}", channelCache.size(), channelCache.keySet());
    }

    /**
     * 路由到指定通道，缺失时抛 {@link BizException}。
     *
     * @param channel 通道类型字符串（大小写无关）
     * @return 对应通道实例
     * @throws BizException 通道为空或不存在
     */
    public MessageChannel route(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new BizException("消息通道不能为空");
        }
        MessageChannel target = channelCache.get(channel.trim().toUpperCase());
        if (target == null) {
            throw new BizException("不支持的消息通道: " + channel);
        }
        return target;
    }

    /**
     * 路由并发送消息：记录开始时间，发送后输出耗时日志，异常捕获返回 fail。
     *
     * @param request 消息请求
     * @return 发送结果
     */
    public MessageResult dispatch(MessageRequest request) {
        String channel = request.getChannel();
        MessageChannel target = route(channel);
        long start = System.currentTimeMillis();
        try {
            MessageResult result = target.send(request);
            long cost = System.currentTimeMillis() - start;
            log.info("[ChannelRouter] channel={} status={} costMs={}",
                    channel, result.getStatus(), cost);
            return result;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("[ChannelRouter] channel={} 发送异常 costMs={}", channel, cost, e);
            return MessageResult.fail(channel, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 判断通道是否启用，结合 {@code pmis.message.channel-enabled.*} 配置。
     * 配置未显式指定时默认启用。
     *
     * @param channel 通道类型字符串（大小写无关）
     * @return true 表示启用
     */
    public boolean isChannelEnabled(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        String key = channel.trim().toUpperCase();
        Map<String, Boolean> enabled = messageProperties.getChannelEnabled();
        if (enabled == null) {
            return true;
        }
        Boolean val = enabled.get(key);
        return val == null || val;
    }

    /**
     * 获取已注册通道的只读视图（供诊断 / 测试使用）。
     *
     * @return 通道缓存只读 Map
     */
    public Map<String, MessageChannel> getChannelCache() {
        return Collections.unmodifiableMap(channelCache);
    }
}
