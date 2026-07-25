package com.njydsz.system.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * System config Feign client.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-system", contextId = "configClient",
        fallbackFactory = ConfigClientFallback.class)
public interface ConfigClient {

    @GetMapping("/api/internal/config/get")
    String getConfig(@RequestParam String key);

    @GetMapping("/api/internal/dict/item")
    Object getDictItem(@RequestParam String typeCode, @RequestParam String itemCode);
}
