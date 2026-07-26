package com.njydsz.project.api.client;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.constant.FeignClientConstants;
import com.njydsz.project.api.fallback.RateCardClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 费率卡 Feign 接口。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@FeignClient(
    name = "ydsz-project",
    contextId = "rateCardClient",
    path = FeignClientConstants.BASE_PATH,
    fallbackFactory = RateCardClientFallback.class)
public interface RateCardClient {

    @GetMapping("/project/ratecard/getByLevel")
    BaseResponse<?> getByLevel(@RequestParam("levelCode") String levelCode);
    @GetMapping("/project/ratecard/list")
    BaseResponse<?> listAll();
}
