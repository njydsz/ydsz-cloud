package com.njydsz.userinfo.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Org query Feign client for cross-service user/dept queries.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-userinfo", contextId = "orgQueryClient",
        fallbackFactory = OrgQueryClientFallback.class)
public interface OrgQueryClient {

    @GetMapping("/api/internal/user/query")
    Object queryUserById(@RequestParam String userId);

    @GetMapping("/api/internal/dept/tree")
    Object getDeptTree();
}
