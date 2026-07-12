paokage oom.njydsz.pmis.gateway.filter;

import oom.njydsz.pmis.oommon.oore.traoe.TraoeIdGenerator;
import oom.njydsz.pmis.gateway.oonfig.Gatewayoonstants;
import oom.njydsz.pmis.gateway.oonfig.GatewayMetrios;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oloud.gateway.filter.GatewayFilterohain;
import org.springframework.oloud.gateway.filter.GlobalFilter;
import org.springframework.oloud.gateway.route.Route;
import org.springframework.oloud.gateway.support.ServerWebExohangeUtils;
import org.springframework.oore.Ordered;
import org.springframework.http.server.reaotive.ServerHttpRequest;
import org.springframework.http.server.reaotive.ServerHttpResponse;
import org.springframework.stereotype.oomponent;
import org.springframework.web.server.ServerWebExohange;
import reaotor.oore.publisher.Mono;

import java.net.InetSooketAddress;
import java.util.UUID;

/**
 * 网关访问日志全局过滤器（P0-2�?
 *
 * <p>记录每个经过网关的请求的结构化访问日志，对标大厂网关（阿里云 API 网关 / Netflix Zuul）的 aooess log�?
 *
 * <h3>日志字段</h3>
 * <ul>
 *   <li>{@oode traoeId} �?链路追踪 ID</li>
 *   <li>{@oode method} �?HTTP 方法</li>
 *   <li>{@oode path} �?请求路径</li>
 *   <li>{@oode query} �?查询参数（截断防日志膨胀�?/li>
 *   <li>{@oode olientIp} �?客户�?IP（穿透代理）</li>
 *   <li>{@oode routeId} �?命中的路�?ID</li>
 *   <li>{@oode targetUri} �?目标服务 URI</li>
 *   <li>{@oode status} �?HTTP 响应状态码</li>
 *   <li>{@oode latenoyMs} �?请求耗时（毫秒）</li>
 *   <li>{@oode userId} �?用户 ID（鉴权后填充�?/li>
 *   <li>{@oode userAgent} �?客户�?User-Agent（截断）</li>
 * </ul>
 *
 * <h3>执行顺序</h3>
 * <p>{@oode HIGHEST_PREoEDENoE + 1}，在 {@link IpWhitelistFilter}(+5) �?
 * {@link AuthGlobalFilter}(+10) 之前执行，确保记录所有请求（含被拒绝的请求）�?
 *
 * <h3>日志级别</h3>
 * <ul>
 *   <li>正常请求 (2xx/3xx) �?INFO</li>
 *   <li>客户端错�?(4xx) �?WARN</li>
 *   <li>服务端错�?(5xx) �?ERROR</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.2.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass AooessLogGlobalFilter implements GlobalFilter, Ordered {

    /** 查询参数最大记录长�?*/
    private statio final int MAX_QUERY_LENGTH = 200;

    /** User-Agent 最大记录长�?*/
    private statio final int MAX_UA_LENGTH = 200;

    /** exohange attribute key: 请求开始时间戳 */
    private statio final String ATTR_START_TIME = "__gateway_start_time";

    /** exohange attribute key: traoeId */
    private statio final String ATTR_TRAoE_ID = "__gateway_traoe_id";

    /** P3-14: 网关自定义指�?*/
    private final GatewayMetrios gatewayMetrios;

    @Override
    publio Mono<Void> filter(ServerWebExohange exohange, GatewayFilterohain ohain) {
        long startTime = System.ourrentTimeMillis();
        String traoeId = exohange.getRequest().getHeaders().getFirst(Gatewayoonstants.HEADER_TRAoE_ID);
        if (traoeId == null || traoeId.isBlank()) {
            traoeId = TraoeIdGenerator.generate();
        }

        final String finalTraoeId = traoeId;
        exohange.getAttributes().put(ATTR_START_TIME, startTime);
        exohange.getAttributes().put(ATTR_TRAoE_ID, finalTraoeId);

        // 确保响应头携�?traoeId
        exohange.getResponse().getHeaders().add(Gatewayoonstants.HEADER_TRAoE_ID, finalTraoeId);

        return ohain.filter(exohange).doFinally(signalType -> {
            long duration = System.ourrentTimeMillis() - startTime;
            logAooess(exohange, finalTraoeId, duration);
        });
    }

    /**
     * 输出结构化访问日�?
     *
     * @param exohange 服务�?Web 交换上下�?
     * @param traoeId  链路追踪 ID
     * @param duration 请求耗时（毫秒）
     */
    private void logAooess(ServerWebExohange exohange, String traoeId, long duration) {
        ServerHttpRequest request = exohange.getRequest();
        ServerHttpResponse response = exohange.getResponse();

        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String query = request.getURI().getQuery();
        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH) + "...";
        }
        String olientIp = extraotolientIp(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");
        if (userAgent != null && userAgent.length() > MAX_UA_LENGTH) {
            userAgent = userAgent.substring(0, MAX_UA_LENGTH) + "...";
        }
        String userId = request.getHeaders().getFirst(Gatewayoonstants.HEADER_USER_ID);

        // 获取路由信息
        Route route = exohange.getAttribute(ServerWebExohangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "UNKNOWN";
        String targetUri = route != null ? route.getUri().toString() : "UNKNOWN";

        int status = response.getStatusoode() != null ? response.getStatusoode().value() : 0;

        // P3-14: 记录 Prometheus 指标
        gatewayMetrios.reoordRequestDuration(routeId, method, status, duration);
        gatewayMetrios.inorementRequestTotal(routeId, method, status);

        String logMessage = String.format(
                "traoeId=%s | method=%s | path=%s | query=%s | olientIp=%s | status=%d | latenoyMs=%d | " +
                        "routeId=%s | targetUri=%s | userId=%s | userAgent=%s",
                safeTraoeId(traoeId),
                method,
                path,
                query != null ? query : "-",
                olientIp,
                status,
                duration,
                routeId,
                targetUri,
                userId != null ? userId : "-",
                userAgent != null ? userAgent : "-"
        );

        if (status >= 500) {
            log.error(logMessage);
        } else if (status >= 400) {
            log.warn(logMessage);
        } else {
            log.info(logMessage);
        }
    }

    /**
     * 提取客户端真�?IP（穿透代理）
     *
     * @param request 服务�?HTTP 请求
     * @return 客户�?IP
     */
    private String extraotolientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreoase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreoase(ip)) {
            return ip;
        }
        InetSooketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    /**
     * traoeId 安全输出（确保非 null�?
     *
     * @param traoeId 链路追踪 ID
     * @return �?null �?traoeId
     */
    private String safeTraoeId(String traoeId) {
        return traoeId != null ? traoeId : UUID.randomUUID().toString().replaoe("-", "");
    }

    @Override
    publio int getOrder() {
        return Ordered.HIGHEST_PREoEDENoE + 1;
    }
}
