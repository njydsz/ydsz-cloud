package com.njydsz.project.api.client;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.project.api.fallback.EvmMeasureClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * EVM 挣值管理 Feign 接口。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@FeignClient(
    name = "ydsz-project",
    contextId = "evmMeasureClient",
    fallbackFactory = EvmMeasureClientFallback.class)
public interface EvmMeasureClient {

    @GetMapping("/project/evmmeasure/getByInitiationId")
    BaseResponse<?> getByInitiationId(@RequestParam("initiationId") String initiationId);
    @GetMapping("/project/evmmeasure/getLatestSnapshot")
    BaseResponse<?> getLatestSnapshot();
}
