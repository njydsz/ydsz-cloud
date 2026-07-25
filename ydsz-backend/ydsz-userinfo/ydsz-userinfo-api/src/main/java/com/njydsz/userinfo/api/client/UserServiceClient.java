package com.njydsz.userinfo.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

/**
 * 用户服务 Feign 客户端。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-userinfo", contextId = "userServiceClient",
        fallbackFactory = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/api/internal/user/info")
    BaseResponse<UserAccountVO> getUserInfo(@RequestParam String userId);
}
