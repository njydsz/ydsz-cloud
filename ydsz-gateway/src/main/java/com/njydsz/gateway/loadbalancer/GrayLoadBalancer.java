package com.njydsz.gateway.loadbalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;

import com.njydsz.common.util.id.RandomUtils;

import reactor.core.publisher.Mono;

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
 * @since 1.0.0
 * @author ydsz-team
 */
public class GrayLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(GrayLoadBalancer.class);

    /** 灰度标签请求头名,同时作为 exchange attribute key */
    public static final String GRAY_TAG_HEADER = "X-Gray-Tag";

    /** 灰度标识值:灰度实例 */
    private static final String GRAY_TAG_GRAY = "gray";

    /** Nacos metadata 中版本标识 key */
    private static final String METADATA_VERSION = "version";

    /**
     * P3-5: Nacos metadata 中权重标识 key
     * <p>实例 metadata 中 weight=10 表示该实例权重为 10（默认 1）。
     * 加权轮询时高权重实例获得更多请求。
     */
    private static final String METADATA_WEIGHT = "weight";

    /**
     * P1-6: 灰度流量比例 key（当 X-Gray-Tag 未指定时，按比例自动分流到灰度）
     * <p>metadata 中 grayRatio=10 表示 10% 流量走灰度。
     */
    private static final String METADATA_GRAY_RATIO = "grayRatio";

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
        this.position = new AtomicInteger(RandomUtils.randomInt(1000));
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

        // P3-5: 加权轮询选择（读取 Nacos metadata 中的 weight 字段）
        ServiceInstance selected = selectByWeight(filtered);

        if (log.isDebugEnabled()) {
            Map<String, String> meta = selected.getMetadata();
            String selectedVersion = meta == null ? null : meta.get(METADATA_VERSION);
            String selectedWeight = meta == null ? null : meta.get(METADATA_WEIGHT);
            log.debug("[GrayLB] 服务 {} 选择实例 {} (grayTag={}, version={}, weight={}, 候选 {} 个)",
                    serviceId, selected.getInstanceId(), grayTag, selectedVersion, selectedWeight, filtered.size());
        }
        return new DefaultResponse(selected);
    }

    /**
     * P3-5: 加权轮询选择
     *
     * <p>读取每个实例的 Nacos metadata {@code weight} 字段（默认 1），
     * 按权重比例随机选择实例。权重越高的实例被选中概率越大。
     * <p>当所有实例权重相同时退化为随机轮询。
     *
     * @param instances 候选实例列表
     * @return 选中的实例
     */
    private ServiceInstance selectByWeight(List<ServiceInstance> instances) {
        // 计算总权重
        int totalWeight = 0;
        int[] weights = new int[instances.size()];
        for (int i = 0; i < instances.size(); i++) {
            int w = getInstanceWeight(instances.get(i));
            weights[i] = w;
            totalWeight += w;
        }

        // 总权重为 0 或所有权重相同，使用随机选择
        if (totalWeight <= 0) {
            int idx = Math.abs(position.incrementAndGet()) % instances.size();
            return instances.get(idx);
        }

        // 加权随机选择
        int random = RandomUtils.randomInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < instances.size(); i++) {
            cumulative += weights[i];
            if (random < cumulative) {
                return instances.get(i);
            }
        }
        // 兜底：返回最后一个
        return instances.get(instances.size() - 1);
    }

    /**
     * P3-5: 获取实例权重
     *
     * @param instance 服务实例
     * @return 权重值（默认 1）
     */
    private int getInstanceWeight(ServiceInstance instance) {
        Map<String, String> meta = instance.getMetadata();
        if (meta == null) {
            return 1;
        }
        String weightStr = meta.get(METADATA_WEIGHT);
        if (weightStr == null || weightStr.isBlank()) {
            return 1;
        }
        try {
            int w = Integer.parseInt(weightStr.trim());
            return w > 0 ? w : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
