package com.njydsz.common.feign.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.ObjectProvider;
import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * 动态 Feign 客户端工厂。
 *
 * <p>维护 Feign 客户端实例缓存，当配置发生变化时自动重建客户端。
 *
 * <p>主要功能：
 * <ul>
 *   <li>缓存 Feign.Builder 实例，避免重复创建</li>
 *   <li>配置变化时清除缓存并重建</li>
 *   <li>支持排除特定客户端不参与重建</li>
 *   <li>支持 per-client 超时配置（通过 {@code ydsz.feign.client-timeouts}）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FeignConfigRefresher
 * @see FeignProperties
 */
@Slf4j
public class DynamicFeignClientFactory {

    /**
     * Feign.Builder 实例缓存。
     * Key: 客户端名称，Value: Feign.Builder 实例
     */
    private final Map<String, Feign.Builder> builderCache = new ConcurrentHashMap<>();

    /** Feign 代理实例缓存（二级缓存），Key = "clientName|targetClass|url" */
    private final Map<String, Object> instanceCache = new ConcurrentHashMap<>();

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
     * 清除缓存并重建 Feign.Builder 和实例。
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

        // 同步清除受影响的实例缓存
        if (!clearedClients.isEmpty()) {
            instanceCache.keySet().removeIf(key ->
                    clearedClients.stream().anyMatch(name -> key.startsWith(name + "|")));
        }

        if (!clearedClients.isEmpty()) {
            log.info("[Feign] 已清除 {} 个 Feign.Builder 缓存: {}", clearedClients.size(), clearedClients);
        }
    }

    /**
     * 完全清除所有缓存（Builder + 实例）。
     */
    public void clearAllCache() {
        builderCache.clear();
        instanceCache.clear();
        log.info("[Feign] 已清除全部缓存");
    }

    /**
     * 获取当前缓存的实例数量。
     *
     * @return 缓存的实例数量
     */
    public int getCachedInstanceCount() {
        return instanceCache.size();
    }

    /**
     * 获取或创建 Feign 代理实例（带二级缓存）。
     *
     * <p>避免每次调用都通过反射创建新的代理对象，高频调用场景下减少不必要的开销。
     * 注意：当配置变更（通过 {@link #clearCache(Set)} 清除 Builder 缓存）时，
     * 实例缓存会同步清除，确保下次创建使用新的 Builder 配置。
     *
     * @param clientName  客户端名称
     * @param targetClass Feign 接口类
     * @param url         目标 URL
     * @param <T>         接口类型
     * @return Feign 代理实例
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCreateInstance(String clientName, Class<T> targetClass, String url) {
        String cacheKey = clientName + "|" + targetClass.getName() + "|" + url;
        return (T) instanceCache.computeIfAbsent(cacheKey,
                k -> getBuilder(clientName).target(targetClass, url));
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

        // 配置超时（支持 per-client 超时配置）
        Request.Options options = buildRequestOptions(clientName);
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
     * <p>优先使用 per-client 超时配置（{@code ydsz.feign.client-timeouts.<clientName>}），
     * 未配置的客户端使用全局 {@code ydsz.feign.timeout}。
     *
     * @param clientName 客户端名称
     * @return Request.Options 实例
     */
    private Request.Options buildRequestOptions(String clientName) {
        FeignProperties.Timeout timeoutConfig = feignProperties.getTimeout();

        FeignProperties.Timeout clientTimeout = feignProperties.getClientTimeouts() != null
                ? feignProperties.getClientTimeouts().get(clientName)
                : null;
        if (clientTimeout != null) {
            log.debug("[Feign] 使用 per-client 超时配置, clientName={}, connect={}ms, read={}ms",
                    clientName, clientTimeout.getConnect(), clientTimeout.getRead());
            return new Request.Options(
                    clientTimeout.getConnect(),
                    TimeUnit.MILLISECONDS,
                    clientTimeout.getRead(),
                    TimeUnit.MILLISECONDS,
                    true
            );
        }

        return new Request.Options(
                timeoutConfig.getConnect(),
                TimeUnit.MILLISECONDS,
                timeoutConfig.getRead(),
                TimeUnit.MILLISECONDS,
                true
        );
    }
}
