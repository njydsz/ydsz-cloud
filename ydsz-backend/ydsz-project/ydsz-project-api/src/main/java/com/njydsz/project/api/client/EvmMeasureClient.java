package com.njydsz.project.api.client;

import com.njydsz.common.core.model.Result;
import com.njydsz.common.feign.constant.FeignClientConstants;
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
    path = FeignClientConstants.BASE_PATH)
public interface EvmMeasureClient {

    @GetMapping("/project/evmmeasure/getByInitiationId")
    Result<?> getByInitiationId(@RequestParam("initiationId") String initiationId);
    @GetMapping("/project/evmmeasure/getLatestSnapshot")
    Result<?> getLatestSnapshot();
}
