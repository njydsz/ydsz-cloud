package com.njydsz.pmis.gateway.loadbalancer;

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
 * <h3>加载机制</h3>
 * <p>{@link LoadBalancerClientFactory} 为每个 serviceId 创建独立的子上下文,
 * 子上下文中通过 {@code environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME)}
 * 获取当前 serviceId,从而为每个服务构建独立的 {@link GrayLoadBalancer} 实例
 * (含独立的轮询计数器)。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
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
