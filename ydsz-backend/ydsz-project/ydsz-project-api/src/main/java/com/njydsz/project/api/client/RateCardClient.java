package com.njydsz.project.api.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.fallback.RateCardClientFallback;

/**
 * 费率卡 Feign 接口。
 *
 * <p>路径与 {@code RateCardController} 的 {@code @RequestMapping("/api/v1/project/rate/card")} 对齐。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(
    name = "ydsz-project",
    contextId = "rateCardClient",
    fallbackFactory = RateCardClientFallback.class)
public interface RateCardClient {

    @GetMapping("/api/v1/project/rate/card/{id}")
    BaseResponse<Map<String, Object>> getById(@PathVariable("id") String id);

    @GetMapping("/api/v1/project/rate/card/list")
    BaseResponse<List<Map<String, Object>>> listAll();
}
