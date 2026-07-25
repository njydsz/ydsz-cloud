package com.njydsz.system.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * App info Feign client for OAuth2 client_id validation.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-system", contextId = "appInfoClient",
        fallbackFactory = AppInfoClientFallback.class)
public interface AppInfoClient {

    @GetMapping("/api/internal/app/validate")
    boolean validateClient(@RequestParam String appKey, @RequestParam String appSecret);
}
