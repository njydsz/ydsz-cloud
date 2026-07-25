package com.njydsz.system.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * System config Feign client.
 *
 * <p>Provides cross-service access to system configuration and dictionary items.
 *
 * @author ydsz-team
 */
@FeignClient(name = "ydsz-system", contextId = "configClient",
        fallbackFactory = ConfigClientFallback.class)
public interface ConfigClient {

    /**
     * 按配置键查询配置值（走缓存）。
     *
     * @param key 配置键
     * @return 配置值，降级时返回空字符串
     */
    @GetMapping("/api/internal/config/get")
    String getConfig(@RequestParam String key);

    /**
     * 按类型编码和字典项编码查询字典项。
     *
     * @param typeCode 字典类型编码
     * @param itemCode 字典项编码
     * @return 字典项 JSON，降级时返回空 JSON 对象
     */
    @GetMapping("/api/internal/dict/item")
    String getDictItem(@RequestParam String typeCode, @RequestParam String itemCode);
}
