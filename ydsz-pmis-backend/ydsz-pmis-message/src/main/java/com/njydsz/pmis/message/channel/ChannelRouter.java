package com.njydsz.pmis.message.channel;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.entity.core.MsgLogDO;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
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

    /** 熔断器缓存：channelType(大写) -> CircuitBreaker */
    private final Map<String, CircuitBreaker> breakerCache = new HashMap<>();

    /** 默认熔断配置：50% 失败率触发熔断,开启 30s,半开试探 3 次 */
    private static final CircuitBreakerConfig DEFAULT_CB_CONFIG = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .build();

    /**
     * 收集所有 MessageChannel Bean 并按通道类型注册,同时为每个通道创建独立熔断器。
     */
    @PostConstruct
    public void initChannels() {
        Map<String, MessageChannel> beans = applicationContext.getBeansOfType(MessageChannel.class);
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(DEFAULT_CB_CONFIG);
        for (MessageChannel channel : beans.values()) {
            String type = channel.channelType() == null ? "" : channel.channelType().trim().toUpperCase();
            if (type.isEmpty()) {
                log.warn("[ChannelRouter] 跳过 channelType 为空的通道: {}", channel.getClass().getName());
                continue;
            }
            channelCache.put(type, channel);
            breakerCache.put(type, registry.circuitBreaker("ch-" + type, DEFAULT_CB_CONFIG));
        }
        log.info("[ChannelRouter] 已注册 {} 个消息通道(含熔断器): {}", channelCache.size(), channelCache.keySet());
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "消息通道不能为空");
        }
        MessageChannel target = channelCache.get(channel.trim().toUpperCase());
        if (target == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "不支持的消息通道: " + channel);
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
        CircuitBreaker breaker = breakerCache.get(channel.trim().toUpperCase());
        // 熔断开启时快速失败,不调用真实通道
        if (breaker != null && !breaker.tryAcquirePermission()) {
            log.warn("[ChannelRouter] 通道熔断中,快速失败: channel={} state={}",
                    channel, breaker.getState());
            return MessageResult.fail(channel, "通道熔断中,请稍后重试");
        }
        long start = System.currentTimeMillis();
        try {
            MessageResult result = target.send(request);
            long cost = System.currentTimeMillis() - start;
            log.info("[ChannelRouter] channel={} status={} costMs={} cbState={}",
                    channel, result.getStatus(), cost,
                    breaker == null ? "N/A" : breaker.getState());
            // 业务失败(非异常)也计入熔断失败率
            if (breaker != null) {
                if (result.isSuccess()) {
                    breaker.onSuccess(cost, java.util.concurrent.TimeUnit.MILLISECONDS);
                } else {
                    breaker.onError(cost, java.util.concurrent.TimeUnit.MILLISECONDS,
                            new RuntimeException(result.getErrorMessage()));
                }
            }
            return result;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            if (breaker != null) {
                breaker.onError(cost, java.util.concurrent.TimeUnit.MILLISECONDS, e);
            }
            log.error("[ChannelRouter] channel={} 发送异常 costMs={} cbState={}",
                    channel, cost, breaker == null ? "N/A" : breaker.getState(), e);
            return MessageResult.fail(channel, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 基于 {@link MsgLogDO} 的分发重载：将日志实体转换为 {@link MessageRequest} 后委托
     * {@link #dispatch(MessageRequest)} 执行，便于上层 service 直接传入日志实体。
     *
     * <p>返回供应商侧追踪 ID（{@code providerTraceId}）；发送失败时抛 {@link BizException}，
     * 由调用方 catch 处理。
     *
     * @param logDO 消息日志实体
     * @return 供应商侧追踪 ID
     * @throws BizException 发送失败
     */
    public String dispatch(MsgLogDO logDO) {
        if (logDO == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "消息日志为空");
        }
        MessageRequest request = new MessageRequest();
        request.setChannel(logDO.getChannel());
        request.setReceiver(logDO.getReceiver());
        request.setContent(logDO.getContent());
        request.setBizType(logDO.getBizType());
        request.setBizId(logDO.getBizId());
        request.setTemplateCode(logDO.getTemplateCode());
        request.setMessageId(logDO.getMsgId());
        String templateParams = logDO.getTemplateParams();
        if (templateParams != null && !templateParams.isBlank()) {
            try {
                request.setParams(JsonUtils.parseMap(templateParams));
            } catch (Exception e) {
                log.warn("[ChannelRouter] templateParams 解析失败,忽略: msgId={}, err={}",
                        logDO.getMsgId(), e.getMessage());
            }
        }
        MessageResult result = dispatch(request);
        if (!result.isSuccess()) {
            throw new BizException(result.getErrorMessage());
        }
        return result.getProviderTraceId();
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
