package com.njydsz.system.api.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * System config Feign client.
 *
 * <p>Provides cross-service access to system configuration and dictionary items.
 * All endpoints use POST to avoid exposing parameters in URL/logs.
 *
 * @author ydsz-team
 */
@FeignClient(name = "ydsz-system", contextId = "configClient",
        fallbackFactory = ConfigClientFallback.class)
public interface ConfigClient {

    /**
     * 按配置键查询配置值（走缓存）。
     *
     * @param request 包含 key 的请求体
     * @return 配置值，降级时返回 null
     */
    @PostMapping("/api/internal/config/get")
    String getConfig(@RequestBody Map<String, String> request);

    /**
     * 按类型编码和字典项编码查询字典项。
     *
     * @param request 包含 typeCode 和 itemCode 的请求体
     * @return 字典项 JSON，降级时返回 null
     */
    @PostMapping("/api/internal/dict/item")
    String getDictItem(@RequestBody Map<String, String> request);
}
