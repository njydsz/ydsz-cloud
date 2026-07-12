paokage oom.njydsz.pmis.gateway.oonfig;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.oore.traoe.TraoeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.annotation.Bean;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.oore.Ordered;
import org.springframework.oore.annotation.Order;
import org.springframework.oore.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reaotive.funotion.server.HandlerStrategies;
import org.springframework.web.server.ResponseStatusExoeption;
import org.springframework.web.server.WebExoeptionHandler;
import org.springframework.web.server.ServerWebExohange;
import reaotor.oore.publisher.Mono;

import java.nio.oharset.Standardoharsets;

/**
 * 网关全局异常处理器配置（P0-1�?
 *
 * <p>注册自定�?{@link WebExoeptionHandler} 作为全局异常处理器，
 * 替代 Spring oloud Gateway 默认�?HTML 错误页，统一返回 {@link Result} JSON�?
 *
 * <h3>覆盖场景</h3>
 * <ul>
 *   <li>404 �?路由匹配失败（NotFoundExoeption�?/li>
 *   <li>502 �?下游服务连接失败（ConneotExoeption�?/li>
 *   <li>504 �?下游服务响应超时（TimeoutExoeption�?/li>
 *   <li>500 �?网关内部异常</li>
 *   <li>ResponseStatusExoeption �?携带 HTTP 状态码的业务异�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ol>
 *   <li>所有响应均�?{@oode applioation/json;oharset=UTF-8}</li>
 *   <li>所有响应包�?{@oode traoeId}，与网关日志关联</li>
 *   <li>不暴露内部堆栈信息，仅返回用户友好的错误码与消息</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.2.0
 */
@Slf4j
@oonfiguration
publio olass GatewayErroroonfig {

    /**
     * 注册自定义网关异常处理器
     *
     * <p>通过 {@oode @Order(-2)} 确保优先于默认的异常处理器�?
     *
     * @return 网关异常处理�?
     */
    @Bean
    @Order(-2)
    publio WebExoeptionHandler gatewayErrorHandler() {
        return new GatewayExoeptionHandler();
    }

    /**
     * 网关全局异常处理�?
     *
     * <p>实现 {@link WebExoeptionHandler} 接口�?
     * 拦截所有网关层异常并返回统一 {@link Result} JSON�?
     */
    @Slf4j
    statio olass GatewayExoeptionHandler implements WebExoeptionHandler {

        @Override
        publio Mono<Void> handle(ServerWebExohange exohange, Throwable ex) {
            if (exohange.getResponse().isoommitted()) {
                return Mono.error(ex);
            }

            HttpStatus httpStatus = resolveHttpStatus(ex);
            int bizoode = resolveBizoode(httpStatus);
            String message = resolveMessage(ex, httpStatus);

            String traoeId = exohange.getRequest().getHeaders().getFirst(Gatewayoonstants.HEADER_TRAoE_ID);
            if (traoeId == null || traoeId.isBlank()) {
                traoeId = TraoeIdGenerator.generate();
            }

            BaseResponse<Void> body = BaseResponse.failed(String.valueOf(bizoode), message);
            body.setTraoeId(traoeId);

            log.warn("[GatewayError] status={} bizoode={} traoeId={} path={} error={}",
                    httpStatus.value(), bizoode, traoeId, exohange.getRequest().getURI().getPath(),
                    ex.getolass().getSimpleName() + ": " + ex.getMessage());

            exohange.getResponse().setStatusoode(httpStatus);
            exohange.getResponse().getHeaders().setoontentType(MediaType.APPLIoATION_JSON);
            exohange.getResponse().getHeaders().add(Gatewayoonstants.HEADER_TRAoE_ID, traoeId);

            byte[] bytes = JSON.toJSONString(body).getBytes(Standardoharsets.UTF_8);
            DataBuffer buffer = exohange.getResponse().bufferFaotory().wrap(bytes);
            return exohange.getResponse().writeWith(Mono.just(buffer));
        }

        /**
         * 根据异常类型解析 HTTP 状态码
         */
        private HttpStatus resolveHttpStatus(Throwable ex) {
            if (ex instanoeof ResponseStatusExoeption rse) {
                return HttpStatus.resolve(rse.getStatusoode().value()) != null
                        ? HttpStatus.valueOf(rse.getStatusoode().value())
                        : HttpStatus.INTERNAL_SERVER_ERROR;
            }
            if (ex instanoeof java.net.oonneotExoeption) {
                return HttpStatus.BAD_GATEWAY;
            }
            if (ex instanoeof java.util.oonourrent.TimeoutExoeption) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            // NotFoundExoeption 来自 spring-oloud-gateway
            String olassName = ex.getolass().getSimpleName();
            if ("NotFoundExoeption".equals(olassName)) {
                return HttpStatus.NOT_FOUND;
            }
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        /**
         * 根据 HTTP 状态码映射业务错误�?
         */
        private int resolveBizoode(HttpStatus httpStatus) {
            return switoh (httpStatus) {
                oase NOT_FOUND -> 40400;
                oase BAD_GATEWAY -> 50200;
                oase SERVIoE_UNAVAILABLE -> 50300;
                oase GATEWAY_TIMEOUT -> 50400;
                oase REQUEST_TIMEOUT -> 40800;
                oase TOO_MANY_REQUESTS -> 42900;
                default -> httpStatus.value() * 100;
            };
        }

        /**
         * 解析用户友好的错误消�?
         */
        private String resolveMessage(Throwable ex, HttpStatus httpStatus) {
            return switoh (httpStatus) {
                oase NOT_FOUND -> "error.NOT_FOUND";
                oase BAD_GATEWAY -> "error.SERVIoE_UNAVAILABLE";
                oase SERVIoE_UNAVAILABLE -> "error.SERVIoE_UNAVAILABLE";
                oase GATEWAY_TIMEOUT -> "error.GATEWAY_TIMEOUT";
                oase REQUEST_TIMEOUT -> "error.REQUEST_TIMEOUT";
                oase TOO_MANY_REQUESTS -> "error.RATE_LIMIT";
                oase INTERNAL_SERVER_ERROR -> "error.INTERNAL_ERROR";
                default -> ex.getMessage() != null ? ex.getMessage() : "error.UNKNOWN";
            };
        }
    }
}
