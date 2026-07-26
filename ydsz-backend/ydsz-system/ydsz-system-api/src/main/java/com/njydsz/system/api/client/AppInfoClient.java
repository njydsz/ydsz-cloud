package com.njydsz.system.api.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * App info Feign client for OAuth2 client_id validation.
 *
 * <p>Uses POST to avoid exposing appSecret in URL query parameters.
 *
 * @author ydsz-team
 */
@FeignClient(name = "ydsz-system", contextId = "appInfoClient",
        fallbackFactory = AppInfoClientFallback.class)
public interface AppInfoClient {

    @PostMapping("/api/internal/app/validate")
    boolean validateClient(@RequestBody Map<String, String> request);
}
