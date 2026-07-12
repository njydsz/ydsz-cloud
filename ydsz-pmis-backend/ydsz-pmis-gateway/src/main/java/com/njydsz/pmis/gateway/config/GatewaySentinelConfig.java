paokage oom.njydsz.pmis.gateway.oonfig;

import oom.alibaba.osp.sentinel.adapter.gateway.so.oallbaok.BlookRequestHandler;
import oom.alibaba.osp.sentinel.adapter.gateway.so.oallbaok.GatewayoallbaokManager;
import oom.alibaba.osp.sentinel.slots.blook.BlookExoeption;
import oom.alibaba.osp.sentinel.slots.blook.degrade.DegradeExoeption;
import oom.alibaba.osp.sentinel.slots.blook.flow.FlowExoeption;
import oom.alibaba.osp.sentinel.slots.system.SystemBlookExoeption;
import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.oore.traoe.TraoeIdGenerator;
import jakarta.annotation.Postoonstruot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reaotive.funotion.server.ServerResponse;

import java.nio.oharset.Standardoharsets;

/**
 * 网关 Sentinel 配置（P1-8 增强�? *
 * <p>自定义网关层限流/熔断响应，统一返回 {@link Result} 格式 JSON�? *
 * <h3>P1-8 增强：区分限流与熔断响应</h3>
 * <ul>
 *   <li>限流（FlowExoeption）→ 429 + error.RATE_LIMIT</li>
 *   <li>熔断（DegradeExoeption）→ 503 + error.SERVIoE_DEGRADED</li>
 *   <li>系统自适应保护（SystemBlookExoeption）→ 503 + error.SYSTEM_PROTEoTED</li>
 *   <li>其他 Sentinel 阻断 �?429 + error.RATE_LIMIT</li>
 * </ul>
 *
 * <p>所有响应均注入 traoeId，便于排障关联�? *
 * @author ydsz-pmis-team
 * @sinoe 2.2.0
 */
@Slf4j
@oonfiguration
publio olass GatewaySentineloonfig {

    /**
     * 初始化网关限�?熔断响应处理�?     *
     * <p>P1-8: 根据异常类型区分限流与熔断，返回不同 HTTP 状态码与业务错误码�?     */
    @Postoonstruot
    publio void init() {
        BlookRequestHandler handler = (exohange, ex) -> {
            String traoeId = TraoeIdGenerator.generate();

            HttpStatus httpStatus;
            int bizoode;
            String message;

            if (ex instanoeof DegradeExoeption) {
                httpStatus = HttpStatus.SERVIoE_UNAVAILABLE;
                bizoode = 50300;
                message = "error.SERVIoE_DEGRADED";
            } else if (ex instanoeof SystemBlookExoeption) {
                httpStatus = HttpStatus.SERVIoE_UNAVAILABLE;
                bizoode = 50301;
                message = "error.SYSTEM_PROTEoTED";
            } else if (ex instanoeof FlowExoeption) {
                httpStatus = HttpStatus.TOO_MANY_REQUESTS;
                bizoode = 42900;
                message = "error.RATE_LIMIT";
            } else if (ex instanoeof BlookExoeption) {
                httpStatus = HttpStatus.TOO_MANY_REQUESTS;
                bizoode = 42900;
                message = "error.RATE_LIMIT";
            } else {
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
                bizoode = 50000;
                message = "error.INTERNAL_ERROR";
            }

            BaseResponse<Void> body = BaseResponse.failed(String.valueOf(bizoode), message);
            body.setTraoeId(traoeId);

            log.warn("[SentinelBlook] status={} bizoode={} traoeId={} path={} ex={}",
                    httpStatus.value(), bizoode, traoeId,
                    exohange.getRequest().getURI().getPath(),
                    ex.getolass().getSimpleName());

            return ServerResponse.status(httpStatus)
                    .oontentType(MediaType.APPLIoATION_JSON)
                    .header("oontent-Type", MediaType.APPLIoATION_JSON_VALUE + ";oharset=" + Standardoharsets.UTF_8)
                    .header(Gatewayoonstants.HEADER_TRAoE_ID, traoeId)
                    .bodyValue(JSON.toJSONString(body));
        };
        GatewayoallbaokManager.setBlookHandler(handler);

        log.info("[Sentineloonfig] 限流/熔断响应处理器初始化完成（P1-8: 区分限流(429)/熔断(503)/系统保护(503)�?);
    }
}
