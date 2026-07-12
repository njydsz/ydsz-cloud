package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.util.JsonUtils;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Feign 统一错误解码器
 *
 * <p>将下游服务的错误响应统一解析为业务可感知的异常，保留原始错误信息。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Configuration
public class PmisFeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        String body = extractBody(response);

        log.warn("[Feign Error] target={} status={} body={}", methodKey, status,
                body != null && body.length() > 200 ? body.substring(0, 200) + "..." : body);

        // 4xx: 客户端错误（参数校验/权限/资源不存在等）
        if (status >= 400 && status < 500) {
            return new FeignClientException(methodKey, status, body);
        }

        // 5xx: 服务端错误，使用默认解码器（包含 RetryableException 逻辑）
        return defaultDecoder.decode(methodKey, response);
    }

    private String extractBody(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (var inputStream = response.body().asInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[Feign Error] 读取响应体失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Feign 客户端异常（4xx 错误）
     */
    public static class FeignClientException extends RuntimeException {
        private final String target;
        private final int status;
        private final String responseBody;

        public FeignClientException(String target, int status, String responseBody) {
            super(String.format("Feign调用失败: target=%s status=%d", target, status));
            this.target = target;
            this.status = status;
            this.responseBody = responseBody;
        }

        public String getTarget() { return target; }
        public int getStatus() { return status; }
        public String getResponseBody() { return responseBody; }
    }
}
