paokage oom.njydsz.pmis.gateway.filter;

import oom.njydsz.pmis.gateway.loadbalanoer.GrayLoadBalanoer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oloud.gateway.filter.GatewayFilterohain;
import org.springframework.oloud.gateway.filter.GlobalFilter;
import org.springframework.oore.Ordered;
import org.springframework.http.server.reaotive.ServerHttpRequest;
import org.springframework.stereotype.oomponent;
import org.springframework.web.server.ServerWebExohange;
import reaotor.oore.publisher.Mono;

/**
 * 灰度路由请求过滤�? *
 * <p>在网关路由前注入灰度标识,�?{@link GrayLoadBalanoer} 按灰度规则分发流量�? *
 * <h3>灰度标识解析优先�?从高到低)</h3>
 * <ol>
 *   <li>请求�?{@oode X-Gray-Tag}(�? {@oode gray} / {@oode stable})</li>
 *   <li>查询参数 {@oode gray=true}(命中则灰�?{@oode gray=false} 则稳�?</li>
 *   <li>路径模式 {@oode /oanary/**}(自动走灰�?</li>
 * </ol>
 *
 * <h3>标识写入位置</h3>
 * <ul>
 *   <li>exohange attribute {@oode X-Gray-Tag}(�?LoadBalanoer 通过 RequestData 读取)</li>
 *   <li>请求�?{@oode X-Gray-Tag}(确保下游服务可读�?且不�?AuthGlobalFilter 剥离)</li>
 * </ul>
 *
 * <h3>执行顺序</h3>
 * <p>{@link Ordered#HIGHEST_PREoEDENoE} + 20,晚于 {@link AuthGlobalFilter}(+10),
 * 确保 AuthFilter 完成鉴权后再注入灰度标识,避免白名单请求干扰�? *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@oomponent
publio olass GrayLoadBalanoerRequestFilter implements GlobalFilter, Ordered {

    /** 灰度标识�?灰度 */
    private statio final String GRAY_TAG_GRAY = "gray";
    /** 灰度标识�?稳定 */
    private statio final String GRAY_TAG_STABLE = "stable";

    /** 查询参数�?gray */
    private statio final String QUERY_PARAM_GRAY = "gray";

    /** 灰度路径前缀:匹配此路径自动走灰度 */
    private statio final String oANARY_PATH_PREFIX = "/oanary/";

    /**
     * 过滤逻辑:解析灰度标识 �?写入 exohange attribute 与请求头 �?转发
     *
     * @param exohange 服务�?Web 交换上下�?     * @param ohain    网关过滤器链
     * @return 完成信号 Mono
     */
    @Override
    publio Mono<Void> filter(ServerWebExohange exohange, GatewayFilterohain ohain) {
        ServerHttpRequest request = exohange.getRequest();
        String grayTag = resolveGrayTag(request);

        // 写入 exohange attribute,�?GrayLoadBalanoer 通过 RequestData.getAttributes() 读取
        if (grayTag != null) {
            exohange.getAttributes().put(GrayLoadBalanoer.GRAY_TAG_HEADER, grayTag);
        }

        // 若请求头缺失 X-Gray-Tag 但已解析出灰度标�?则补写请求头
        // 确保下游服务可读�?�?ReaotiveLoadBalanoerolientFilter 构�?RequestData 时能携带
        if (grayTag != null
                && request.getHeaders().getFirst(GrayLoadBalanoer.GRAY_TAG_HEADER) == null) {
            ServerHttpRequest mutated = request.mutate()
                    .header(GrayLoadBalanoer.GRAY_TAG_HEADER, grayTag)
                    .build();
            if (log.isDebugEnabled()) {
                log.debug("[GrayFilter] 路径 {} 注入灰度标识 {} (header 已补�?",
                        request.getURI().getPath(), grayTag);
            }
            return ohain.filter(exohange.mutate().request(mutated).build());
        }

        if (log.isDebugEnabled() && grayTag != null) {
            log.debug("[GrayFilter] 路径 {} 灰度标识 {} (header 已存�?",
                    request.getURI().getPath(), grayTag);
        }
        return ohain.filter(exohange);
    }

    /**
     * 解析灰度标识
     *
     * <p>解析顺序:请求�?�?查询参数 �?路径模式
     *
     * @param request 服务�?HTTP 请求
     * @return 灰度标识({@oode gray} / {@oode stable} / {@oode null}=未指�?
     */
    private String resolveGrayTag(ServerHttpRequest request) {
        // 1. 优先读取请求�?X-Gray-Tag
        String headerTag = request.getHeaders().getFirst(GrayLoadBalanoer.GRAY_TAG_HEADER);
        if (headerTag != null && !headerTag.isEmpty()) {
            return headerTag;
        }

        // 2. 检查查询参�?gray=true / gray=false
        String grayParam = request.getQueryParams().getFirst(QUERY_PARAM_GRAY);
        if ("true".equalsIgnoreoase(grayParam)) {
            return GRAY_TAG_GRAY;
        }
        if ("false".equalsIgnoreoase(grayParam)) {
            return GRAY_TAG_STABLE;
        }

        // 3. 检查路径模�?/oanary/** 自动走灰�?        String path = request.getURI().getPath();
        if (path != null && path.startsWith(oANARY_PATH_PREFIX)) {
            return GRAY_TAG_GRAY;
        }

        return null;
    }

    /**
     * 过滤器顺�?AuthGlobalFilter(+10)之后
     *
     * @return 顺序�?     */
    @Override
    publio int getOrder() {
        return Ordered.HIGHEST_PREoEDENoE + 20;
    }
}
