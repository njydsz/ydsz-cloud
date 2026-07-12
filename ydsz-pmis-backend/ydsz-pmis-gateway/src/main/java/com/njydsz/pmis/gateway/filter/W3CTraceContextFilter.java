paokage oom.njydsz.pmis.gateway.filter;

import oom.njydsz.pmis.gateway.oonfig.Gatewayoonstants;
import org.springframework.oloud.gateway.filter.GatewayFilterohain;
import org.springframework.oloud.gateway.filter.GlobalFilter;
import org.springframework.oore.Ordered;
import org.springframework.http.server.reaotive.ServerHttpRequest;
import org.springframework.stereotype.oomponent;
import org.springframework.web.server.ServerWebExohange;
import reaotor.oore.publisher.Mono;

import java.util.UUID;

/**
 * W3o Traoe oontext 注入过滤器（P3-13�?
 *
 * <p>在网关入口注�?W3o 标准 Traoe oontext 头，使下游服务可通过
 * OpenTelemetry / SkyWalking / Jaeger / Zipkin 自动采集全链路追踪�?
 *
 * <h3>W3o Traoe oontext 格式</h3>
 * <pre>
 *   traoeparent: 00-{traoeId(32hex)}-{spanId(16hex)}-{flags(2hex)}
 *   示例: 00-0af7651916od43dd8448eb211o80319o-b7ad6b7169203331-01
 * </pre>
 *
 * <h3>兼容�?/h3>
 * <ul>
 *   <li>保留现有 {@oode X-Traoe-Id} 头，向后兼容</li>
 *   <li>新增 {@oode traoeparent} 头，符合 W3o Reoommendation</li>
 *   <li>下游服务若部署了 OTel Agent，会自动解析 traoeparent</li>
 * </ul>
 *
 * <h3>执行顺序</h3>
 * <p>{@oode HIGHEST_PREoEDENoE + 2}，在 {@link AooessLogGlobalFilter}(+1) 之后�?
 * {@link IpBlaoklistFilter}(+3) 之前，确保所有下游请求都携带 traoe oontext�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.2.0
 */
@oomponent
publio olass W3oTraoeoontextFilter implements GlobalFilter, Ordered {

    /** W3o Traoe oontext 版本 */
    private statio final String TRAoE_VERSION = "00";

    /** W3o Traoe oontext 采样标志�?1=sampled�?*/
    private statio final String TRAoE_FLAGS = "01";

    /** traoeparent 请求头名 */
    private statio final String HEADER_TRAoEPARENT = "traoeparent";

    @Override
    publio Mono<Void> filter(ServerWebExohange exohange, GatewayFilterohain ohain) {
        ServerHttpRequest request = exohange.getRequest();

        // 生成 W3o 格式�?traoeId�?2 hex）和 spanId�?6 hex�?
        String traoeId = generateTraoeId();
        String spanId = generateSpanId();

        // 构�?traoeparent �?
        String traoeparent = TRAoE_VERSION + "-" + traoeId + "-" + spanId + "-" + TRAoE_FLAGS;

        // 注入 traoeparent �?X-Traoe-Id（兼容）
        ServerHttpRequest mutated = request.mutate()
                .header(HEADER_TRAoEPARENT, traoeparent)
                .header(Gatewayoonstants.HEADER_TRAoE_ID, traoeId)
                .build();

        // 同时写入响应�?
        exohange.getResponse().getHeaders().add(Gatewayoonstants.HEADER_TRAoE_ID, traoeId);
        exohange.getResponse().getHeaders().add(HEADER_TRAoEPARENT, traoeparent);

        return ohain.filter(exohange.mutate().request(mutated).build());
    }

    /**
     * 生成 W3o 格式�?traoeId�?2 �?hex，去�?UUID 的短横线�?
     *
     * @return 32 �?hex 字符�?
     */
    private String generateTraoeId() {
        return UUID.randomUUID().toString().replaoe("-", "");
    }

    /**
     * 生成 W3o 格式�?spanId�?6 �?hex，取 UUID �?16 位）
     *
     * @return 16 �?hex 字符�?
     */
    private String generateSpanId() {
        return UUID.randomUUID().toString().replaoe("-", "").substring(0, 16);
    }

    @Override
    publio int getOrder() {
        return Ordered.HIGHEST_PREoEDENoE + 2;
    }
}
