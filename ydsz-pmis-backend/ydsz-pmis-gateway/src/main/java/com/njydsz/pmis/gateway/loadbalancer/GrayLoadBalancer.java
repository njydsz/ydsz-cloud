package com.njydsz.pmis.gateway.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网关层灰度负载均衡器
 *
 * <p>基于 Spring Cloud LoadBalancer 的 {@link ReactorServiceInstanceLoadBalancer} 实现,
 * 按请求头 {@code X-Gray-Tag} 与 Nacos 实例 metadata 中的 {@code version} 标签
 * 进行灰度流量分发。
 *
 * <h3>路由规则</h3>
 * <ol>
 *   <li>读取请求头 {@code X-Gray-Tag}(值: {@code gray} / {@code stable} / 空)</li>
 *   <li>读取 Nacos metadata 中实例的 {@code version} 标签
 *       (metadata.key = "version", value = "gray" / "stable")</li>
 *   <li>若 {@code X-Gray-Tag=gray},只选择 {@code metadata.version=gray} 的实例</li>
 *   <li>若 {@code X-Gray-Tag=stable} 或无 Header,只选择 {@code metadata.version!=gray} 的实例</li>
 *   <li>在候选实例中用轮询(RoundRobin)选择</li>
 *   <li>若灰度实例不存在,降级到所有实例轮询(避免灰度实例下线时 503)</li>
 * </ol>
 *
 * <h3>灰度标识来源(优先级从高到低)</h3>
 * <ul>
 *   <li>请求头 {@code X-Gray-Tag}(由 {@link GrayLoadBalancerRequestFilter} 注入)</li>
 *   <li>exchange attribute {@code X-Gray-Tag}(Filter 写入的备份)</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class GrayLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(GrayLoadBalancer.class);

    /** 灰度标签请求头名,同时作为 exchange attribute key */
    public static final String GRAY_TAG_HEADER = "X-Gray-Tag";

    /** 灰度标识值:灰度实例 */
    private static final String GRAY_TAG_GRAY = "gray";

    /** Nacos metadata 中版本标识 key */
    private static final String METADATA_VERSION = "version";

    /** 服务实例列表供给者(延迟加载,每个 serviceId 对应独立的子上下文) */
    private final ObjectProvider<ServiceInstanceListSupplier> supplierProvider;

    /** 当前负载均衡器所属服务 ID */
    private final String serviceId;

    /** 轮询位置计数器(AtomicInteger 保证线程安全) */
    private final AtomicInteger position;

    /**
     * 构造灰度负载均衡器
     *
     * @param supplierProvider 服务实例列表供给者
     * @param serviceId        服务 ID
     */
    public GrayLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> supplierProvider,
                            String serviceId) {
        this.supplierProvider = supplierProvider;
        this.serviceId = serviceId;
        // 初始位置随机化,避免多实例启动时首轮都命中同一实例
        this.position = new AtomicInteger(ThreadLocalRandom.current().nextInt(1000));
    }

    /**
     * 响应式选择实例(带请求上下文)
     *
     * <p>从请求上下文中提取灰度标识,然后按灰度规则过滤实例并轮询选择。
     *
     * @param request 负载均衡请求(携带 HTTP 头与 exchange attributes)
     * @return 实例响应 Mono
     */
    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        String grayTag = resolveGrayTag(request);

        ServiceInstanceListSupplier supplier = supplierProvider.getIfAvailable();
        if (supplier == null) {
            log.warn("[GrayLB] 服务 {} 无可用 ServiceInstanceListSupplier", serviceId);
            return Mono.just(new EmptyResponse());
        }

        return supplier.get(request).next()
                .map(instances -> getInstanceResponse(instances, grayTag))
                .onErrorResume(e -> {
                    log.warn("[GrayLB] 服务 {} 获取实例列表失败: {}", serviceId, e.getMessage());
                    return Mono.just(new EmptyResponse());
                });
    }

    /**
     * 响应式选择实例(无请求上下文)
     *
     * <p>父接口默认方法会传入空的 DefaultRequest,此处复用 {@link #choose(Request)}。
     *
     * @return 实例响应 Mono
     */
    @Override
    public Mono<Response<ServiceInstance>> choose() {
        return choose(null);
    }

    /**
     * 从请求上下文中解析灰度标识
     *
     * <p>解析顺序:
     * <ol>
     *   <li>HTTP 请求头 {@code X-Gray-Tag}</li>
     *   <li>exchange attribute {@code X-Gray-Tag}(由 GrayLoadBalancerRequestFilter 写入)</li>
     * </ol>
     *
     * @param request 负载均衡请求
     * @return 灰度标识({@code gray} / {@code stable} / {@code null})
     */
    @SuppressWarnings("rawtypes")
    private String resolveGrayTag(Request request) {
        if (request == null) {
            return null;
        }
        Object context = request.getContext();
        if (!(context instanceof RequestDataContext rdc)) {
            return null;
        }
        RequestData data = rdc.getClientRequest();
        if (data == null) {
            return null;
        }

        // 1. 优先从 HTTP Header 读取
        HttpHeaders headers = data.getHeaders();
        if (headers != null) {
            String headerTag = headers.getFirst(GRAY_TAG_HEADER);
            if (headerTag != null && !headerTag.isEmpty()) {
                return headerTag;
            }
        }

        // 2. 回退到 exchange attributes(Filter 写入的备份)
        Map<String, Object> attrs = data.getAttributes();
        if (attrs != null) {
            Object attrTag = attrs.get(GRAY_TAG_HEADER);
            if (attrTag instanceof String s && !s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    /**
     * 按灰度规则过滤并轮询选择实例
     *
     * <p>步骤:
     * <ol>
     *   <li>按灰度标识过滤候选实例</li>
     *   <li>过滤后为空则降级使用全量实例(避免灰度实例下线时 503)</li>
     *   <li>在候选实例中轮询选择</li>
     * </ol>
     *
     * @param instances 全量服务实例
     * @param grayTag   灰度标识
     * @return 实例响应
     */
    private Response<ServiceInstance> getInstanceResponse(List<ServiceInstance> instances,
                                                          String grayTag) {
        if (instances == null || instances.isEmpty()) {
            log.warn("[GrayLB] 服务 {} 无可用实例", serviceId);
            return new EmptyResponse();
        }

        boolean wantGray = GRAY_TAG_GRAY.equalsIgnoreCase(grayTag);

        // 按灰度标签过滤
        List<ServiceInstance> filtered = new ArrayList<>(instances.size());
        for (ServiceInstance inst : instances) {
            Map<String, String> meta = inst.getMetadata();
            String version = meta == null ? null : meta.get(METADATA_VERSION);
            boolean isGray = GRAY_TAG_GRAY.equalsIgnoreCase(version);
            if (wantGray) {
                // 灰度请求:只选 version=gray 的实例
                if (isGray) {
                    filtered.add(inst);
                }
            } else {
                // 稳定请求或无标识:只选 version!=gray 的实例
                if (!isGray) {
                    filtered.add(inst);
                }
            }
        }

        // 降级:灰度实例不存在时使用全量实例,避免 503
        if (filtered.isEmpty()) {
            if (wantGray) {
                log.warn("[GrayLB] 服务 {} 灰度实例不存在,降级到全量实例(共 {} 个)",
                        serviceId, instances.size());
            }
            filtered = instances;
        }

        // 轮询选择(AtomicInteger 自增取模,保证均匀分布)
        int idx = Math.abs(position.incrementAndGet()) % filtered.size();
        ServiceInstance selected = filtered.get(idx);

        if (log.isDebugEnabled()) {
            Map<String, String> meta = selected.getMetadata();
            String selectedVersion = meta == null ? null : meta.get(METADATA_VERSION);
            log.debug("[GrayLB] 服务 {} 选择实例 {} (grayTag={}, version={}, 候选 {} 个)",
                    serviceId, selected.getInstanceId(), grayTag, selectedVersion, filtered.size());
        }
        return new DefaultResponse(selected);
    }
}
