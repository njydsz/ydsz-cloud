package com.njydsz.common.feign.config;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.feign.aspect.FeignRequestInterceptor;
import com.njydsz.common.feign.aspect.YdszFeignErrorDecoder;
import com.njydsz.common.feign.aspect.YdszFeignLogger;
import com.njydsz.common.feign.circuitbreaker.CircuitBreakerStatePersistence;
import com.njydsz.common.feign.circuitbreaker.FeignCircuitBreakerStrategy;
import com.njydsz.common.feign.codec.JsonDecoder;
import com.njydsz.common.feign.codec.JsonEncoder;
import com.njydsz.common.feign.codec.ResponseUnwrapDecoder;
import com.njydsz.common.feign.compress.GzipRequestCompressInterceptor;
import com.njydsz.common.feign.health.FeignHealthIndicator;
import com.njydsz.common.feign.interceptor.BulkheadRequestInterceptor;
import com.njydsz.common.feign.interceptor.FeignResponseInterceptor;
import com.njydsz.common.feign.monitor.FeignResponseMetricsAdapter;
import com.njydsz.common.feign.trace.TraceRequestInterceptor;
import com.njydsz.common.redis.service.ops.RedisStringOps;

import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.ResponseInterceptor;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * YdszFeign 自动配置类。
 *
 * <p>提供 Feign 客户端的全局默认配置，包括：
 * <ul>
 *   <li>请求拦截器：透传数据权限上下文到下游服务</li>
 *   <li>错误解码器：将 HTTP 错误状态映射为业务异常</li>
 *   <li>日志增强：自定义日志格式，提升可观测性</li>
 *   <li>重试策略：指数退避重试，提升调用成功率</li>
 *   <li>超时控制：可配置连接超时和读取超时</li>
 * </ul>
 *
 * <p>配置生效条件：
 * <ul>
 *   <li>classpath 中存在 {@code Feign} 类</li>
 *   <li>配置项 {@code ydsz.feign.enabled=true}（默认为 true）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FeignProperties
 * @see FeignRequestInterceptor
 * @see YdszFeignErrorDecoder
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(Feign.class)
@EnableConfigurationProperties(FeignProperties.class)
@ConditionalOnProperty(prefix = "ydsz.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeignConfiguration {

    /**
     * 创建 YdszFeign 日志处理器。
     * <p>
     * 相比 Feign 默认日志处理器，提供了更丰富的上下文信息，
     * 包括请求耗时、响应状态等，便于问题排查。
     *
     * @return YdszFeignLogger 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public Logger feignLogger() {
        return new YdszFeignLogger();
    }

    /**
     * 创建 Feign 请求拦截器。
     * <p>
     * 透传数据权限相关上下文信息到下游服务，包括：
     * <ul>
     *   <li>身份认证信息（Token、用户类型等）</li>
     *   <li>数据权限上下文（行级权限、列级权限）</li>
     *   <li>用户偏好信息（语言、设备标识等）</li>
     * </ul>
     *
     * @param feignProperties Feign 配置属性
     * @return FeignRequestInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean(FeignRequestInterceptor.class)
    public FeignRequestInterceptor requestInterceptor(FeignProperties feignProperties) {
        return new FeignRequestInterceptor(feignProperties);
    }

    /**
     * 配置 Feign 日志级别。
     * <p>
     * 支持四种日志级别：
     * <ul>
     *   <li>{@link Logger.Level#NONE} - 不记录日志，性能最佳</li>
     *   <li>{@link Logger.Level#BASIC} - 仅记录请求方法、URL 和响应状态（推荐生产环境）</li>
     *   <li>{@link Logger.Level#HEADERS} - 记录基本信息和请求/响应头</li>
     *   <li>{@link Logger.Level#FULL} - 记录完整请求和响应（仅用于调试）</li>
     * </ul>
     *
     * @param feignProperties Feign 配置属性
     * @return Logger.Level 日志级别
     */
    @Bean
    @ConditionalOnMissingBean
    public Logger.Level feignLoggerLevel(FeignProperties feignProperties) {
        return feignProperties.resolvedLoggerLevel();
    }

    /**
     * 创建 Feign 错误解码器。
     * <p>
     * 将 HTTP 响应状态码转换为对应的业务异常：
     * <ul>
     *   <li>400 Bad Request - {@link com.njydsz.common.feign.exception.BadRequestException}</li>
     *   <li>404 Not Found - {@link com.njydsz.common.feign.exception.NotFoundException}</li>
     *   <li>401/403/429/500/503 - {@link com.njydsz.common.feign.exception.OpenFeignException}</li>
     * </ul>
     *
     * @param feignProperties Feign 配置属性
     * @return ErrorDecoder 实例
     */
    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    public ErrorDecoder errorDecoder(FeignProperties feignProperties) {
        return new YdszFeignErrorDecoder(feignProperties);
    }

    /**
     * 创建 Feign 重试器。
     * <p>
     * 使用指数退避算法进行重试：
     * <ul>
     *   <li>初始延迟：{@code 100ms}</li>
     *   <li>最大延迟：{@code 500ms}</li>
     *   <li>延迟倍数：{@code 2.0}</li>
     * </ul>
     * <p>
     * 仅对以下情况重试：
     * <ul>
     *   <li>连接超时</li>
     *   <li>服务器错误（5xx）</li>
     *   <li>指定 HTTP 方法的请求</li>
     * </ul>
     *
     * @param feignProperties Feign 配置属性
     * @return Retryer 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ydsz.feign.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Retryer feignRetryer(FeignProperties feignProperties) {
        FeignProperties.Retry retryConfig = feignProperties.getRetry();
        long period = retryConfig.getBackoff().getDelay();
        long maxPeriod = retryConfig.getBackoff().getMaxDelay();
        int maxAttempts = retryConfig.getMaxAttempts();
        return new MethodAwareRetryer(period, maxPeriod, maxAttempts, retryConfig.getRetryOnMethods());
    }

    /**
     * 创建 Feign JSON 编码器。
     *
     * <p>使用统一 JSON 引擎进行请求体序列化，
     * 替代 Spring Cloud OpenFeign 默认的 SpringEncoder。
     *
     * @return JsonEncoder 实例
     */
    @Bean
    @ConditionalOnMissingBean(Encoder.class)
    public Encoder feignEncoder() {
        return new JsonEncoder();
    }

    /**
     * 创建 Feign 响应解码器。
     *
     * <p>使用 {@link ResponseUnwrapDecoder} 包装 {@link JsonDecoder}，
     * 自动解包 {@code BaseResponse<T>} 响应，直接返回内部 data 字段。
     *
     * @return ResponseUnwrapDecoder 实例
     */
    @Bean
    @ConditionalOnMissingBean(Decoder.class)
    public Decoder feignDecoder() {
        return new ResponseUnwrapDecoder(new JsonDecoder());
    }

    /**
     * 配置 Feign 默认超时时间。
     * <p>
     * 通过覆盖默认的 Options Bean 来设置连接超时和读取超时。
     *
     * @param feignProperties Feign 配置属性
     * @return Request.Options 超时配置
     */
    @Bean
    @ConditionalOnMissingBean
    public Request.Options feignOptions(FeignProperties feignProperties) {
        FeignProperties.Timeout timeoutConfig = feignProperties.getTimeout();
        return new Request.Options(
                timeoutConfig.getConnect(),
                TimeUnit.MILLISECONDS,
                timeoutConfig.getRead(),
                TimeUnit.MILLISECONDS,
                true
        );
    }

    /**
     * 创建链路追踪请求拦截器。
     * <p>
     * 自动为 Feign 请求注入链路追踪相关请求头（X-Trace-Id / X-Span-Id / X-Parent-Span-Id），
     * 实现微服务调用链追踪。
     *
     * @return TraceRequestInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean(TraceRequestInterceptor.class)
    public RequestInterceptor traceRequestInterceptor() {
        return new TraceRequestInterceptor();
    }

    /**
     * 创建 GZIP 请求压缩拦截器。
     * <p>
     * 对 Feign 请求体进行 GZIP 压缩，减少网络传输量。
     * 仅当 {@code ydsz.feign.compress.enabled=true} 时生效。
     *
     * @param feignProperties Feign 配置属性
     * @return GzipRequestCompressInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean(GzipRequestCompressInterceptor.class)
    @ConditionalOnProperty(prefix = "ydsz.feign.compress", name = "enabled", havingValue = "true")
    public RequestInterceptor gzipRequestCompressInterceptor(FeignProperties feignProperties) {
        FeignProperties.Compress compressConfig = feignProperties.getCompress();
        return new GzipRequestCompressInterceptor(
                compressConfig.getMinSize(),
                compressConfig.getExcludedContentTypes()
        );
    }

    /**
     * 创建 Feign 响应拦截器。
     * <p>
     * 统一处理 Feign 客户端的响应，提供以下能力：
     * <ul>
     *   <li>响应日志记录（状态码、耗时、响应头）</li>
     *   <li>响应指标采集（用于 Micrometer 监控）</li>
     *   <li>响应头统一处理（如提取链路追踪信息）</li>
     *   <li>异常响应统一处理</li>
     * </ul>
     * <p>
     * 仅当 {@code ydsz.feign.response-interceptor.enabled=true} 时生效。
     *
     * @param feignProperties        Feign 配置属性
     * @param meterRegistryProvider Micrometer 注册器（可选）
     * @param circuitBreakerProvider 熔断器策略（可选）
     * @param bulkheadProvider      Bulkhead 拦截器（可选，启用后用于在 finally 中释放许可）
     * @return FeignResponseInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean(FeignResponseInterceptor.class)
    @ConditionalOnProperty(prefix = "ydsz.feign.response-interceptor", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ResponseInterceptor feignResponseInterceptor(FeignProperties feignProperties,
                                                          ObjectProvider<MeterRegistry> meterRegistryProvider,
                                                          ObjectProvider<FeignCircuitBreakerStrategy> circuitBreakerProvider,
                                                          ObjectProvider<BulkheadRequestInterceptor> bulkheadProvider) {
        boolean logEnabled = feignProperties.getResponseInterceptor().isLogEnabled();
        long slowCallThresholdMillis = feignProperties.getResponseInterceptor().getSlowCallThresholdMillis();

        FeignResponseInterceptor.FeignResponseMetrics metrics = null;
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (feignProperties.getResponseInterceptor().isMetricsEnabled() && meterRegistry != null) {
            metrics = new FeignResponseMetricsAdapter(meterRegistry);
        }

        FeignCircuitBreakerStrategy circuitBreaker = circuitBreakerProvider.getIfAvailable();
        BulkheadRequestInterceptor bulkhead = bulkheadProvider.getIfAvailable();

        return new FeignResponseInterceptor(metrics, logEnabled, slowCallThresholdMillis, circuitBreaker, bulkhead);
    }

    /**
     * 创建 Bulkhead 请求隔离拦截器。
     * <p>
     * 使用信号量按服务维度限制最大并发请求数，防止某个下游服务变慢耗尽连接池。
     * 仅当 {@code ydsz.feign.bulkhead.enabled=true} 时生效。
     * <p>
     * <b>许可释放：</b>{@link BulkheadRequestInterceptor#apply} 获取许可后写入 ThreadLocal，
     * 由 {@link FeignResponseInterceptor} 在 finally 块中通过 {@code releaseCurrentPermit()} 释放。
     *
     * @param feignProperties Feign 配置属性
     * @return BulkheadRequestInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean(BulkheadRequestInterceptor.class)
    @ConditionalOnProperty(prefix = "ydsz.feign.bulkhead", name = "enabled", havingValue = "true")
    public RequestInterceptor bulkheadRequestInterceptor(FeignProperties feignProperties) {
        FeignProperties.Bulkhead config = feignProperties.getBulkhead();
        return new BulkheadRequestInterceptor(config);
    }

    /**
     * 创建 HttpClient 连接池管理器。
     * <p>
     * 基于 {@link FeignProperties.Client} 配置初始化连接池参数，
     * 优化高并发场景下的连接复用。
     *
     * @param feignProperties Feign 配置属性
     * @return PoolingHttpClientConnectionManager 实例
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(PoolingHttpClientConnectionManager.class)
    @ConditionalOnClass(PoolingHttpClientConnectionManager.class)
    public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager(FeignProperties feignProperties) {
        FeignProperties.Client clientConfig = feignProperties.getClient();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(clientConfig.getMaxConnections());
        cm.setDefaultMaxPerRoute(clientConfig.getMaxPerRoute());
        cm.setValidateAfterInactivity(TimeValue.ofMilliseconds(clientConfig.getValidateAfterInactivity()));
        return cm;
    }

    /**
     * 创建 HttpClient 实例。
     * <p>
     * 使用连接池管理器构建 HttpClient，并配置：
     * <ul>
     *   <li>空闲连接回收：超过 keepAlive 时长未使用的连接自动关闭</li>
     *   <li>过期连接回收：超过连接生命周期（connectionTimeToLive）的连接自动关闭</li>
     *   <li>空闲连接校验：使用前对空闲连接进行探活，防止使用到已被服务端关闭的"僵尸连接"</li>
     *   <li>禁用 Cookie 和自动重定向：Feign 调用不依赖会话状态</li>
     *   <li>禁用自动重试：重试由 {@link Retryer} 统一控制，避免双层重试</li>
     * </ul>
     *
     * <p><b>设计说明：</b>经过调优的连接池配置可显著提升高并发场景下的吞吐量和稳定性。
     *
     * @param connectionManager 连接池管理器
     * @param feignProperties   Feign 配置属性
     * @return CloseableHttpClient 实例
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CloseableHttpClient.class)
    @ConditionalOnClass(CloseableHttpClient.class)
    public CloseableHttpClient httpClient(PoolingHttpClientConnectionManager connectionManager,
                                          FeignProperties feignProperties) {
        FeignProperties.Client clientConfig = feignProperties.getClient();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .evictIdleConnections(TimeValue.ofMilliseconds(clientConfig.getKeepAlive()))
                .evictExpiredConnections()
                .setDefaultRequestConfig(RequestConfig.custom()
                        // 连接获取超时 5s
                        .setConnectionRequestTimeout(5, TimeUnit.SECONDS)
                        .build())
                .disableCookieManagement()
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .build();
    }

    /**
     * 创建动态 Feign 客户端工厂。
     *
     * <p>当启用动态配置刷新时（ydsz.feign.refresh.enabled=true），
     * 通过该工厂管理 Feign 客户端实例的生命周期，支持配置热更新。
     *
     * @param feignProperties        Feign 配置属性
     * @param loggerProvider         日志处理器
     * @param errorDecoderProvider   错误解码器
     * @param retryerProvider        重试器
     * @param interceptorsProvider   请求拦截器列表
     * @param decoderProvider        解码器
     * @param encoderProvider        编码器
     * @return DynamicFeignClientFactory 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DynamicFeignClientFactory dynamicFeignClientFactory(
            FeignProperties feignProperties,
            ObjectProvider<Logger> loggerProvider,
            ObjectProvider<ErrorDecoder> errorDecoderProvider,
            ObjectProvider<Retryer> retryerProvider,
            ObjectProvider<List<RequestInterceptor>> interceptorsProvider,
            ObjectProvider<Decoder> decoderProvider,
            ObjectProvider<Encoder> encoderProvider) {
        return new DynamicFeignClientFactory(
                feignProperties,
                loggerProvider,
                errorDecoderProvider,
                retryerProvider,
                interceptorsProvider,
                decoderProvider,
                encoderProvider);
    }

    /**
     * 创建 Feign 配置刷新监听器。
     *
     * <p>监听 Spring Cloud 的配置变更事件，当 Feign 相关配置发生变化时，
     * 自动重建 Feign 客户端实例以应用新配置。
     * 仅在 {@code spring-cloud-context} 可用时生效。
     *
     * @param applicationContext Spring 应用上下文
     * @param clientFactory      动态 Feign 客户端工厂
     * @return FeignConfigRefresher 实例
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.cloud.context.environment.EnvironmentChangeEvent")
    @ConditionalOnMissingBean
    public FeignConfigRefresher feignConfigRefresher(
            ApplicationContext applicationContext,
            DynamicFeignClientFactory clientFactory) {
        return new FeignConfigRefresher(applicationContext, clientFactory);
    }

    /**
     * 注册 Feign 健康检查指示器。
     *
     * <p>暴露 /actuator/health/feign 端点，报告 Feign 客户端配置概要和熔断器状态。
     *
     * @param feignProperties              Feign 配置属性
     * @param circuitBreakerStrategyProvider 熔断器策略提供者（可选）
     * @return FeignHealthIndicator 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnProperty(prefix = "ydsz.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeignHealthIndicator feignHealthIndicator(
            FeignProperties feignProperties,
            ObjectProvider<FeignCircuitBreakerStrategy> circuitBreakerStrategyProvider) {
        return new FeignHealthIndicator(feignProperties, circuitBreakerStrategyProvider);
    }

    /**
     * 注册熔断器状态持久化组件。
     *
     * <p>当 Redis 在 classpath 中时，将熔断状态持久化到 Redis，
     * 应用重启后可恢复熔断状态。TTL 可通过
     * {@code ydsz.feign.circuit-breaker.state-ttl-seconds} 配置。
     *
     * @param feignProperties      Feign 配置属性
     * @param redisStringOpsProvider Redis String 操作提供者（可选）
     * @return CircuitBreakerStatePersistence 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(RedisStringOps.class)
    public CircuitBreakerStatePersistence circuitBreakerStatePersistence(
            FeignProperties feignProperties,
            ObjectProvider<RedisStringOps> redisStringOpsProvider) {
        int ttlSeconds = feignProperties.getCircuitBreaker().getStateTtlSeconds();
        return new CircuitBreakerStatePersistence(redisStringOpsProvider, Duration.ofSeconds(ttlSeconds));
    }
}
