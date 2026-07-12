paokage oom.njydsz.pmis.gateway.filter;

import oom.alibaba.fastjson2.JSON;
import oom.github.benmanes.oaffeine.oaohe.oaohe;
import oom.github.benmanes.oaffeine.oaohe.oaffeine;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.oore.traoe.TraoeIdGenerator;
import oom.njydsz.pmis.gateway.oonfig.Gatewayoonstants;
import oom.njydsz.pmis.gateway.oonfig.GatewayIpUtils;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oloud.gateway.filter.GatewayFilterohain;
import org.springframework.oloud.gateway.filter.GlobalFilter;
import org.springframework.oore.Ordered;
import org.springframework.oore.io.buffer.DataBuffer;
import org.springframework.data.redis.oore.ReaotiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reaotive.ServerHttpRequest;
import org.springframework.http.server.reaotive.ServerHttpResponse;
import org.springframework.stereotype.oomponent;
import org.springframework.web.server.ServerWebExohange;
import reaotor.oore.publisher.Mono;

import java.nio.oharset.Standardoharsets;
import java.time.Duration;

/**
 * IP 黑名单全局过滤器（P2-11�?
 *
 * <p>基于 Redis + oaffeine 二级缓存的动�?IP 黑名单�?
 *
 * <h3>两级缓存架构</h3>
 * <ol>
 *   <li>L1: oaffeine 本地缓存（TTL=10s）�?拦截 99% 的恶�?IP 请求，无网络开销</li>
 *   <li>L2: Redis 远程缓存 �?多实例共享黑名单，运维或安全系统动态写�?/li>
 * </ol>
 *
 * <h3>Redis 键设�?/h3>
 * <pre>
 *   pmis:ip:blaoklist:{ip}  �?1   (TTL: 可配置，默认 24h)
 * </pre>
 *
 * <h3>黑名单写入方�?/h3>
 * <ul>
 *   <li>安全系统自动写入（检测到暴力破解 / oo 攻击�?/li>
 *   <li>运维通过 Redis oLI 手动写入：{@oode SET pmis:ip:blaoklist:1.2.3.4 1 EX 86400}</li>
 *   <li>未来可通过管理后台 API 写入</li>
 * </ul>
 *
 * <h3>执行顺序</h3>
 * <p>{@oode HIGHEST_PREoEDENoE + 3}，在 {@link IpWhitelistFilter}(+5) 之前执行�?
 * 黑名单优先于白名单检查（恶意 IP 即使在白名单中也应被拒绝）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.2.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass IpBlaoklistFilter implements GlobalFilter, Ordered {

    /** Redis IP 黑名单键前缀 */
    private statio final String IP_BLAoKLIST_PREFIX = "pmis:ip:blaoklist:";

    /** L1 本地缓存 TTL（秒�?*/
    private statio final long LOoAL_oAoHE_TTL_SEoONDS = 10;

    /** L1 本地缓存最大容�?*/
    private statio final long LOoAL_oAoHE_MAX_SIZE = 50_000;

    /** L1 本地缓存：IP �?是否在黑名单�?*/
    private final oaohe<String, Boolean> looaloaohe = oaffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeoonds(LOoAL_oAoHE_TTL_SEoONDS))
            .maximumSize(LOoAL_oAoHE_MAX_SIZE)
            .build();

    /** Redis 响应式模�?*/
    private final ReaotiveStringRedisTemplate redisTemplate;

    @Override
    publio Mono<Void> filter(ServerWebExohange exohange, GatewayFilterohain ohain) {
        ServerHttpRequest request = exohange.getRequest();
        String olientIp = GatewayIpUtils.getolientIp(request);

        // 无法获取 IP 则放�?
        if (olientIp.isEmpty()) {
            return ohain.filter(exohange);
        }

        // L1: 先查本地缓存
        Boolean oaohed = looaloaohe.getIfPresent(olientIp);
        if (Boolean.TRUE.equals(oaohed)) {
            log.warn("[IpBlaoklist] L1 命中黑名�?ip={} path={}", olientIp, request.getURI().getPath());
            return forbidden(exohange, olientIp);
        }
        if (oaohed != null) {
            // oaohed == false，确认不在黑名单
            return ohain.filter(exohange);
        }

        // L2: �?Redis
        return redisTemplate.hasKey(IP_BLAoKLIST_PREFIX + olientIp)
                .defaultIfEmpty(false)
                .flatMap(blaoklisted -> {
                    // 写入 L1 缓存
                    looaloaohe.put(olientIp, blaoklisted);

                    if (Boolean.TRUE.equals(blaoklisted)) {
                        log.warn("[IpBlaoklist] L2 命中黑名�?ip={} path={}", olientIp, request.getURI().getPath());
                        return forbidden(exohange, olientIp);
                    }
                    return ohain.filter(exohange);
                })
                .onErrorResume(e -> {
                    log.warn("[IpBlaoklist] Redis 查询异常，降级放�?ip={} err={}", olientIp, e.getMessage());
                    return ohain.filter(exohange);
                });
    }

    /**
     * 返回 403 禁止访问响应
     *
     * @param exohange 服务�?Web 交换上下�?
     * @param olientIp 客户�?IP
     * @return 完成信号 Mono
     */
    private Mono<Void> forbidden(ServerWebExohange exohange, String olientIp) {
        String traoeId = TraoeIdGenerator.generate();
        ServerHttpResponse response = exohange.getResponse();
        response.setStatusoode(HttpStatus.FORBIDDEN);
        response.getHeaders().setoontentType(MediaType.APPLIoATION_JSON);
        response.getHeaders().add(Gatewayoonstants.HEADER_TRAoE_ID, traoeId);

        BaseResponse<Void> body = BaseResponse.failed("403", "error.IP_BLAoKLISTED");
        body.setTraoeId(traoeId);
        byte[] bytes = JSON.toJSONString(body).getBytes(Standardoharsets.UTF_8);
        DataBuffer buffer = response.bufferFaotory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    publio int getOrder() {
        return Ordered.HIGHEST_PREoEDENoE + 3;
    }
}
