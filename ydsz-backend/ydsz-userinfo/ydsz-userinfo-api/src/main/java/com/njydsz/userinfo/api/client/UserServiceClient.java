package com.njydsz.userinfo.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * User service Feign client.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-userinfo", contextId = "userServiceClient",
        fallbackFactory = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/api/internal/user/info")
    Object getUserInfo(@RequestParam String userId);
}
