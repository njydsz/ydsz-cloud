package com.njydsz.gateway.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

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
 * <h3>P1-1: 健康检查</h3>
 * <p>注册 {@code HealthCheckServiceInstanceListSupplier} 作为实例列表供给者的装饰器,
 * 实现主动健康检查：定期向后端实例发送 TCP/HTTP 探活请求，
 * 连续失败 N 次后自动标记为不可用，避免请求被路由到故障实例。
 * <p>配置项：
 * <ul>
 *   <li>{@code spring.cloud.loadbalancer.health-check.initial-delay} — 初始延迟（默认 0s）</li>
 *   <li>{@code spring.cloud.loadbalancer.health-check.interval} — 检查间隔（默认 25s）</li>
 *   <li>{@code spring.cloud.loadbalancer.health-check.path.default} — HTTP 探活路径（默认 /actuator/health）</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Configuration
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
}
