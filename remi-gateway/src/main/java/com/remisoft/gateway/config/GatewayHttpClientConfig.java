package com.remisoft.gateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 网关 HttpClient 连接池配置（P0-阶段二-6）
 *
 * <p>对标大厂网关的连接池管理，避免高并发下频繁创建/销毁 TCP 连接导致：
 * <ul>
 *   <li>TW（TIME_WAIT）堆积：短连接模式下，每个请求建立+关闭连接，
 *       会导致大量 TIME_WAIT 占用端口资源</li>
 *   <li>连接建立延迟：TCP 三次握手 + TLS 握手耗时 50-200ms，
 *       连接池复用可将延迟降至接近 0</li>
 *   <li>FD 泄漏：连接未正确关闭导致文件描述符泄漏，最终触发 too many open files</li>
 * </ul>
 *
 * <h3>连接池参数</h3>
 * <ul>
 *   <li>{@code maxConnections} — 最大连接数（默认 500，按 4C8G 网关节点估算）</li>
 *   <li>{@code pendingAcquireTimeout} — 获取连接超时（默认 45s，
 *       超过则抛 PoolAcquireTimeoutException）</li>
 *   <li>{@code maxIdleTime} — 最大空闲时间（默认 30s，超时回收）</li>
 *   <li>{@code maxLifeTime} — 最大生命周期（默认 60s，到期强制关闭重建，
 *       避免长连接被中间设备（LVS/SLB/Firewall）静默关闭）</li>
 *   <li>{@code evictionInterval} — 后台驱逐检查间隔（默认 60s）</li>
 * </ul>
 *
 * <h3>Liveness vs Readiness</h3>
 * <p>连接池参数与 K8s 探针配合：
 * <ul>
 *   <li>Liveness probe 失败 → Pod 重启 → 连接池重建</li>
 *   <li>Readiness probe 失败 → 流量切走，但 Pod 不重启 → 连接池保留</li>
 * </ul>
 *
 * @since 1.0.0
 * @author remi-team
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "remi.gateway.httpclient.pool")
@Data
public class GatewayHttpClientConfig {

    /**
     * 最大连接数（每路由维度）
     *
     * <p>默认 500，按 4C8G 网关节点估算：
     * <ul>
     *   <li>每连接占用 ~50KB 内存（含 TLS 上下文）</li>
     *   <li>500 连接 ≈ 25MB 内存，远低于 8GB 总量</li>
     *   <li>QPS 估算：500 连接 × 100ms/请求 = 5000 QPS（满足大厂网关基线）</li>
     * </ul>
     */
    private int maxConnections = 500;

    /**
     * 获取连接超时（毫秒）
     *
     * <p>超过此时间未获取到连接，抛出 PoolAcquireTimeoutException。
     * 默认 45000ms（45s），与下游服务 30s 响应超时 + 缓冲。
     */
    private long pendingAcquireTimeoutMs = 45000;

    /**
     * 最大空闲时间（秒）
     *
     * <p>连接空闲超过此时间后回收，避免持有半关闭连接。
     * 默认 30s。
     */
    private long maxIdleTimeSeconds = 30;

    /**
     * 最大生命周期（秒）
     *
     * <p>连接存活超过此时间后强制关闭重建，
     * 避免被中间设备（LVS/SLB/Firewall）静默关闭导致请求失败。
     * 默认 60s。
     */
    private long maxLifeTimeSeconds = 60;

    /**
     * 后台驱逐检查间隔（秒）
     *
     * <p>Lettuce 风格的后台清理线程，定期扫描过期/泄漏连接。
     * 默认 60s。
     */
    private long evictionIntervalSeconds = 60;

    /**
     * 构建 Reactor Netty 连接提供者
     *
     * <p>Spring Cloud Gateway 4.x 默认使用 Reactor Netty 的
     * {@link ConnectionProvider}，此处覆盖默认配置启用连接池。
     *
     * @return 连接提供者
     */
    @Bean
    public ConnectionProvider gatewayConnectionProvider() {
        ConnectionProvider.Builder builder = ConnectionProvider.builder("remi-gateway-pool")
                .maxConnections(maxConnections)
                .pendingAcquireTimeout(Duration.ofMillis(pendingAcquireTimeoutMs))
                .maxIdleTime(Duration.ofSeconds(maxIdleTimeSeconds))
                .maxLifeTime(Duration.ofSeconds(maxLifeTimeSeconds))
                .evictInBackground(Duration.ofSeconds(evictionIntervalSeconds));

        log.info("[HttpClient] 连接池配置 maxConnections={} pendingAcquireTimeout={}ms maxIdle={}s maxLife={}s evictInterval={}s",
                maxConnections, pendingAcquireTimeoutMs, maxIdleTimeSeconds, maxLifeTimeSeconds, evictionIntervalSeconds);

        return builder.build();
    }

    /**
     * 构建网关 HttpClient
     *
     * <p>使用上述连接提供者创建 HttpClient，覆盖 Spring Cloud Gateway 默认配置。
     * 此 Bean 会被 Spring Cloud Gateway 自动发现并用于代理请求转发。
     *
     * @param connectionProvider 连接提供者
     * @return HttpClient
     */
    @Bean
    public HttpClient gatewayHttpClient(ConnectionProvider connectionProvider) {
        return HttpClient.create(connectionProvider);
    }
}
