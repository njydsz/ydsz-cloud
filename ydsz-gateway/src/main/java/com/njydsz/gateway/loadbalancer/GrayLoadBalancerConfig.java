package com.njydsz.gateway.loadbalancer;

import java.util.function.BiFunction;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.DiscoveryClientServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.HealthCheckServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 灰度负载均衡器配置
 *
 * <p>通过 {@link LoadBalancerClients#defaultConfiguration} 将
 * {@link GrayLoadBalancer} 注册为所有服务的默认负载均衡器,
 * 替换 Spring Cloud LoadBalancer 内置的 {@code RoundRobinLoadBalancer}。
 *
 * <p>配合 {@code spring.cloud.loadbalancer.configurations=gray} 配置项,
 * 抑制默认轮询负载均衡器,使灰度负载均衡器接管所有 {@code lb://} 路由。
 *
 * <h3>P2-1: 可选辅助定位</h3>
 * <p>入口流量拆分由 Argo Rollouts 统一控制（Infrastructue 层）。
 * 本模块降为可选的辅助组件,仅用于服务间调用透传灰度标记
 * （header-based 路由,如压测、定向灰度验证）。
 * 通过 {@code ydsz.gray-loadbalancer.enabled=true} 控制是否启用
 * （默认 true,保持向后兼容）。
 *
 * <h3>P1-1/P2-10: 主动健康检查（已启用）</h3>
 * <p>注册 {@link HealthCheckServiceInstanceListSupplier} 作为实例列表供给者的装饰器,
 * 实现主动健康检查：定期向后端实例发送 HTTP 探活请求，
 * 连续失败 N 次后自动标记为不可用，避免请求被路由到故障实例。
 * <p>配置项：
 * <ul>
 *   <li>{@code spring.cloud.loadbalancer.health-check.initial-delay} — 初始延迟（默认 0s）</li>
 *   <li>{@code spring.cloud.loadbalancer.health-check.interval} — 检查间隔（默认 25s）</li>
 *   <li>{@code spring.cloud.loadbalancer.health-check.path.default} — HTTP 探活路径（默认 /actuator/health）</li>
 *   <li>{@code spring.cloud.loadbalancer.health-check.refetch-instances} — 是否刷新实例列表（默认 true）</li>
 * </ul>
 *
 * <p><b>降级：</b>健康检查装饰器异常（如探活超时）不影响请求主链路，
 * 仅将该实例标记为不可用；若所有实例均不可用则退化为全量列表（fail-open）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Configuration
@ConditionalOnProperty(value = "ydsz.gray-loadbalancer.enabled", havingValue = "true", matchIfMissing = true)
@LoadBalancerClients(defaultConfiguration = GrayLoadBalancerConfig.class)
public class GrayLoadBalancerConfig {

    /**
     * 注册灰度负载均衡器 Bean
     *
     * <p>Bean 名称 {@code reactorServiceInstanceLoadBalancer} 与 Spring Cloud LoadBalancer
     * 默认实现一致,配合 {@code @ConditionalOnMissingBean} 覆盖默认的 RoundRobinLoadBalancer。
     *
     * @param environment 子上下文环境(携带 serviceId 属性)
     * @param factory     负载均衡客户端工厂(提供延迟加载的实例列表供给者)
     * @return 灰度负载均衡器
     */
    @Bean
    ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(
            Environment environment, LoadBalancerClientFactory factory) {
        String serviceId = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new GrayLoadBalancer(
                factory.getLazyProvider(serviceId, ServiceInstanceListSupplier.class),
                serviceId);
    }

    /**
     * P2-10: 注册带主动健康检查的实例列表供给者
     *
     * <p>以 Nacos 服务发现为基础，叠加 {@link HealthCheckServiceInstanceListSupplier} 装饰器：
     * 网关按 {@code spring.cloud.loadbalancer.health-check.interval}（默认 25s）周期探活，
     * 探活路径默认 {@code /actuator/health}。连续失败实例从候选列表中剔除，
     * 故障发现延迟从 Nacos 心跳（15s）缩短到探活周期级别。
     *
     * <p>说明：spring-cloud-loadbalancer 5.0.2 无静态 {@code decorator} 工厂方法，
     * 改为手动构造并注入 HTTP 探活函数（GET /actuator/health，2xx 视为存活；
     * 探活异常视为不可用，fail-open 语义）。
     *
     * @param discoveryClient Nacos 服务发现客户端
     * @param environment     子上下文环境
     * @param factory         负载均衡客户端工厂（探活周期配置读取）
     * @return 健康检查装饰后的实例列表供给者
     */
    @Bean
    ServiceInstanceListSupplier serviceInstanceListSupplier(
            DiscoveryClient discoveryClient, Environment environment,
            LoadBalancerClientFactory factory) {
        // 基类供给者：从 Nacos 拉取服务实例
        ServiceInstanceListSupplier base =
                new DiscoveryClientServiceInstanceListSupplier(
                        discoveryClient, environment);

        // HTTP 探活函数：GET http://{instance}/{path}，2xx 视为存活
        WebClient webClient = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024))
                .build();
        BiFunction<ServiceInstance, String, Mono<Boolean>> healthChecker =
                (instance, path) -> webClient.get()
                        .uri(instance.getUri().toString() + path)
                        .exchangeToMono(resp -> Mono.just(resp.statusCode().is2xxSuccessful()))
                        .onErrorReturn(false);

        ServiceInstanceListSupplier withHealthCheck =
                new HealthCheckServiceInstanceListSupplier(base, factory, healthChecker);
        log.info("[GrayLB] 已启用主动健康检查（间隔 {}ms，探活路径 /actuator/health）",
                environment.getProperty("spring.cloud.loadbalancer.health-check.interval", "25000"));
        return withHealthCheck;
    }
}
