package com.njydsz.pmis.common.feign.config;

import feign.Feign;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 动态 Feign 客户端工厂。
 *
 * <p>维护 Feign 客户端实例缓存，当配置发生变化时自动重建客户端。
 * 结合 {@link RefreshScope} 实现配置热更新。
 *
 * <p>主要功能：
 * <ul>
 *   <li>缓存 Feign.Builder 实例，避免重复创建</li>
 *   <li>配置变化时清除缓存并重建</li>
 *   <li>支持排除特定客户端不参与重建</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see FeignConfigRefresher
 * @see FeignProperties
 */
@Slf4j
@RefreshScope
public class DynamicFeignClientFactory {

    /**
     * Feign.Builder 实例缓存。
     * Key: 客户端名称，Value: Feign.Builder 实例
     */
    private final Map<String, Feign.Builder> builderCache = new ConcurrentHashMap<>();

    /** Feign 配置属性 */
    private final FeignProperties feignProperties;
    /** Feign 日志处理器提供者 */
    private final ObjectProvider<Logger> loggerProvider;
    /** 错误解码器提供者 */
    private final ObjectProvider<ErrorDecoder> errorDecoderProvider;
    /** 重试器提供者 */
    private final ObjectProvider<Retryer> retryerProvider;
    /** 请求拦截器列表提供者 */
    private final ObjectProvider<List<RequestInterceptor>> interceptorsProvider;
    /** 解码器提供者 */
    private final ObjectProvider<Decoder> decoderProvider;
    /** 编码器提供者 */
    private final ObjectProvider<Encoder> encoderProvider;

    /**
     * 构造函数。
     *
     * @param feignProperties       Feign 配置属性
     * @param loggerProvider        日志处理器
     * @param errorDecoderProvider  错误解码器
     * @param retryerProvider       重试器
     * @param interceptorsProvider  请求拦截器列表
     * @param decoderProvider       解码器
     * @param encoderProvider       编码器
     */
    public DynamicFeignClientFactory(FeignProperties feignProperties,
                                     ObjectProvider<Logger> loggerProvider,
                                     ObjectProvider<ErrorDecoder> errorDecoderProvider,
                                     ObjectProvider<Retryer> retryerProvider,
                                     ObjectProvider<List<RequestInterceptor>> interceptorsProvider,
                                     ObjectProvider<Decoder> decoderProvider,
                                     ObjectProvider<Encoder> encoderProvider) {
        this.feignProperties = feignProperties;
        this.loggerProvider = loggerProvider;
        this.errorDecoderProvider = errorDecoderProvider;
        this.retryerProvider = retryerProvider;
        this.interceptorsProvider = interceptorsProvider;
        this.decoderProvider = decoderProvider;
        this.encoderProvider = encoderProvider;
    }

    /**
     * 获取或创建 Feign.Builder 实例。
     *
     * @param clientName 客户端名称
     * @return Feign.Builder 实例
     */
    public Feign.Builder getBuilder(String clientName) {
        return builderCache.computeIfAbsent(clientName, this::createBuilder);
    }

    /**
     * 获取默认的 Feign.Builder（无特定客户端名称）。
     *
     * @return Feign.Builder 实例
     */
    public Feign.Builder getDefaultBuilder() {
        return getBuilder("default");
    }

    /**
     * 清除缓存并重建 Feign.Builder。
     *
     * @param excludeClients 排除的客户端名称集合
     */
    public void clearCache(Set<String> excludeClients) {
        Set<String> clientNames = builderCache.keySet();
        List<String> clearedClients = new ArrayList<>();

        for (String clientName : clientNames) {
            if (excludeClients != null && excludeClients.contains(clientName)) {
                log.debug("[Feign] 跳过排除的客户端: {}", clientName);
                continue;
            }
            builderCache.remove(clientName);
            clearedClients.add(clientName);
        }

        if (!clearedClients.isEmpty()) {
            log.info("[Feign] 已清除 {} 个 Feign.Builder 缓存: {}", clearedClients.size(), clearedClients);
        }
    }

    /**
     * 获取当前缓存的客户端数量。
     *
     * @return 缓存的客户端数量
     */
    public int getCachedClientCount() {
        return builderCache.size();
    }

    /**
     * 创建新的 Feign.Builder 实例。
     *
     * @param clientName 客户端名称
     * @return Feign.Builder 实例
     */
    private Feign.Builder createBuilder(String clientName) {
        log.info("[Feign] 创建 Feign.Builder, clientName={}", clientName);

        Feign.Builder builder = Feign.builder();

        // 配置日志
        Logger logger = loggerProvider.getIfAvailable();
        if (logger != null) {
            builder.logger(logger);
            builder.logLevel(feignProperties.resolvedLoggerLevel());
        }

        // 配置错误解码器
        ErrorDecoder errorDecoder = errorDecoderProvider.getIfAvailable();
        if (errorDecoder != null) {
            builder.errorDecoder(errorDecoder);
        }

        // 配置重试器
        Retryer retryer = retryerProvider.getIfAvailable();
        if (retryer != null) {
            builder.retryer(retryer);
        }

        // 配置请求拦截器
        List<RequestInterceptor> interceptors = interceptorsProvider.getIfAvailable(() -> new ArrayList<>());
        for (RequestInterceptor interceptor : interceptors) {
            builder.requestInterceptor(interceptor);
        }

        // 配置超时
        Request.Options options = buildRequestOptions();
        builder.options(options);

        // 配置解码器和编码器
        Decoder decoder = decoderProvider.getIfAvailable();
        if (decoder != null) {
            builder.decoder(decoder);
        }

        Encoder encoder = encoderProvider.getIfAvailable();
        if (encoder != null) {
            builder.encoder(encoder);
        }

        return builder;
    }

    /**
     * 构建请求超时配置。
     *
     * @return Request.Options 实例
     */
    private Request.Options buildRequestOptions() {
        FeignProperties.Timeout timeoutConfig = feignProperties.getTimeout();
        return new Request.Options(
                timeoutConfig.getConnect(),
                TimeUnit.MILLISECONDS,
                timeoutConfig.getRead(),
                TimeUnit.MILLISECONDS,
                true
        );
    }
}
