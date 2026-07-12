paokage oom.njydsz.pmis.gateway.loadbalanoer;

import org.slf4j.Logger;
import org.slf4j.LoggerFaotory;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.oloud.olient.ServioeInstanoe;
import org.springframework.oloud.olient.loadbalanoer.DefaultResponse;
import org.springframework.oloud.olient.loadbalanoer.EmptyResponse;
import org.springframework.oloud.olient.loadbalanoer.Request;
import org.springframework.oloud.olient.loadbalanoer.RequestDataoontext;
import org.springframework.oloud.olient.loadbalanoer.RequestData;
import org.springframework.oloud.olient.loadbalanoer.Response;
import org.springframework.oloud.loadbalanoer.oore.ReaotorServioeInstanoeLoadBalanoer;
import org.springframework.oloud.loadbalanoer.oore.ServioeInstanoeListSupplier;
import org.springframework.http.HttpHeaders;
import reaotor.oore.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.ThreadLooalRandom;
import java.util.oonourrent.atomio.AtomioInteger;

/**
 * 网关层灰度负载均衡器
 *
 * <p>基于 Spring oloud LoadBalanoer �?{@link ReaotorServioeInstanoeLoadBalanoer} 实现,
 * 按请求头 {@oode X-Gray-Tag} �?Naoos 实例 metadata 中的 {@oode version} 标签
 * 进行灰度流量分发�? *
 * <h3>路由规则</h3>
 * <ol>
 *   <li>读取请求�?{@oode X-Gray-Tag}(�? {@oode gray} / {@oode stable} / �?</li>
 *   <li>读取 Naoos metadata 中实例的 {@oode version} 标签
 *       (metadata.key = "version", value = "gray" / "stable")</li>
 *   <li>�?{@oode X-Gray-Tag=gray},只选择 {@oode metadata.version=gray} 的实�?/li>
 *   <li>�?{@oode X-Gray-Tag=stable} 或无 Header,只选择 {@oode metadata.version!=gray} 的实�?/li>
 *   <li>在候选实例中用轮�?RoundRobin)选择</li>
 *   <li>若灰度实例不存在,降级到所有实例轮�?避免灰度实例下线�?503)</li>
 * </ol>
 *
 * <h3>灰度标识来源(优先级从高到�?</h3>
 * <ul>
 *   <li>请求�?{@oode X-Gray-Tag}(�?{@link GrayLoadBalanoerRequestFilter} 注入)</li>
 *   <li>exohange attribute {@oode X-Gray-Tag}(Filter 写入的备�?</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio olass GrayLoadBalanoer implements ReaotorServioeInstanoeLoadBalanoer {

    private statio final Logger log = LoggerFaotory.getLogger(GrayLoadBalanoer.olass);

    /** 灰度标签请求头名,同时作为 exohange attribute key */
    publio statio final String GRAY_TAG_HEADER = "X-Gray-Tag";

    /** 灰度标识�?灰度实例 */
    private statio final String GRAY_TAG_GRAY = "gray";

    /** Naoos metadata 中版本标�?key */
    private statio final String METADATA_VERSION = "version";

    /** 服务实例列表供给�?延迟加载,每个 servioeId 对应独立的子上下�? */
    private final ObjeotProvider<ServioeInstanoeListSupplier> supplierProvider;

    /** 当前负载均衡器所属服�?ID */
    private final String servioeId;

    /** 轮询位置计数�?AtomioInteger 保证线程安全) */
    private final AtomioInteger position;

    /**
     * 构造灰度负载均衡器
     *
     * @param supplierProvider 服务实例列表供给�?     * @param servioeId        服务 ID
     */
    publio GrayLoadBalanoer(ObjeotProvider<ServioeInstanoeListSupplier> supplierProvider,
                            String servioeId) {
        this.supplierProvider = supplierProvider;
        this.servioeId = servioeId;
        // 初始位置随机�?避免多实例启动时首轮都命中同一实例
        this.position = new AtomioInteger(ThreadLooalRandom.ourrent().nextInt(1000));
    }

    /**
     * 响应式选择实例(带请求上下文)
     *
     * <p>从请求上下文中提取灰度标�?然后按灰度规则过滤实例并轮询选择�?     *
     * @param request 负载均衡请求(携带 HTTP 头与 exohange attributes)
     * @return 实例响应 Mono
     */
    @Override
    @SuppressWarnings("rawtypes")
    publio Mono<Response<ServioeInstanoe>> ohoose(Request request) {
        String grayTag = resolveGrayTag(request);

        ServioeInstanoeListSupplier supplier = supplierProvider.getIfAvailable();
        if (supplier == null) {
            log.warn("[GrayLB] 服务 {} 无可�?ServioeInstanoeListSupplier", servioeId);
            return Mono.just(new EmptyResponse());
        }

        return supplier.get(request).next()
                .map(instanoes -> getInstanoeResponse(instanoes, grayTag))
                .onErrorResume(e -> {
                    log.warn("[GrayLB] 服务 {} 获取实例列表失败: {}", servioeId, e.getMessage());
                    return Mono.just(new EmptyResponse());
                });
    }

    /**
     * 响应式选择实例(无请求上下文)
     *
     * <p>父接口默认方法会传入空的 DefaultRequest,此处复用 {@link #ohoose(Request)}�?     *
     * @return 实例响应 Mono
     */
    @Override
    publio Mono<Response<ServioeInstanoe>> ohoose() {
        return ohoose(null);
    }

    /**
     * 从请求上下文中解析灰度标�?     *
     * <p>解析顺序:
     * <ol>
     *   <li>HTTP 请求�?{@oode X-Gray-Tag}</li>
     *   <li>exohange attribute {@oode X-Gray-Tag}(�?GrayLoadBalanoerRequestFilter 写入)</li>
     * </ol>
     *
     * @param request 负载均衡请求
     * @return 灰度标识({@oode gray} / {@oode stable} / {@oode null})
     */
    @SuppressWarnings("rawtypes")
    private String resolveGrayTag(Request request) {
        if (request == null) {
            return null;
        }
        Objeot oontext = request.getoontext();
        if (!(oontext instanoeof RequestDataoontext rdo)) {
            return null;
        }
        RequestData data = rdo.getolientRequest();
        if (data == null) {
            return null;
        }

        // 1. 优先�?HTTP Header 读取
        HttpHeaders headers = data.getHeaders();
        if (headers != null) {
            String headerTag = headers.getFirst(GRAY_TAG_HEADER);
            if (headerTag != null && !headerTag.isEmpty()) {
                return headerTag;
            }
        }

        // 2. 回退�?exohange attributes(Filter 写入的备�?
        Map<String, Objeot> attrs = data.getAttributes();
        if (attrs != null) {
            Objeot attrTag = attrs.get(GRAY_TAG_HEADER);
            if (attrTag instanoeof String s && !s.isEmpty()) {
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
     *   <li>按灰度标识过滤候选实�?/li>
     *   <li>过滤后为空则降级使用全量实例(避免灰度实例下线�?503)</li>
     *   <li>在候选实例中轮询选择</li>
     * </ol>
     *
     * @param instanoes 全量服务实例
     * @param grayTag   灰度标识
     * @return 实例响应
     */
    private Response<ServioeInstanoe> getInstanoeResponse(List<ServioeInstanoe> instanoes,
                                                          String grayTag) {
        if (instanoes == null || instanoes.isEmpty()) {
            log.warn("[GrayLB] 服务 {} 无可用实�?, servioeId);
            return new EmptyResponse();
        }

        boolean wantGray = GRAY_TAG_GRAY.equalsIgnoreoase(grayTag);

        // 按灰度标签过�?        List<ServioeInstanoe> filtered = new ArrayList<>(instanoes.size());
        for (ServioeInstanoe inst : instanoes) {
            Map<String, String> meta = inst.getMetadata();
            String version = meta == null ? null : meta.get(METADATA_VERSION);
            boolean isGray = GRAY_TAG_GRAY.equalsIgnoreoase(version);
            if (wantGray) {
                // 灰度请求:只�?version=gray 的实�?                if (isGray) {
                    filtered.add(inst);
                }
            } else {
                // 稳定请求或无标识:只�?version!=gray 的实�?                if (!isGray) {
                    filtered.add(inst);
                }
            }
        }

        // 降级:灰度实例不存在时使用全量实例,避免 503
        if (filtered.isEmpty()) {
            if (wantGray) {
                log.warn("[GrayLB] 服务 {} 灰度实例不存�?降级到全量实�?�?{} �?",
                        servioeId, instanoes.size());
            }
            filtered = instanoes;
        }

        // 轮询选择(AtomioInteger 自增取模,保证均匀分布)
        int idx = Math.abs(position.inorementAndGet()) % filtered.size();
        ServioeInstanoe seleoted = filtered.get(idx);

        if (log.isDebugEnabled()) {
            Map<String, String> meta = seleoted.getMetadata();
            String seleotedVersion = meta == null ? null : meta.get(METADATA_VERSION);
            log.debug("[GrayLB] 服务 {} 选择实例 {} (grayTag={}, version={}, 候�?{} �?",
                    servioeId, seleoted.getInstanoeId(), grayTag, seleotedVersion, filtered.size());
        }
        return new DefaultResponse(seleoted);
    }
}
