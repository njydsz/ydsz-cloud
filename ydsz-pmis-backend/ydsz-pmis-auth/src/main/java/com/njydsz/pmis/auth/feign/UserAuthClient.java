package com.njydsz.pmis.auth.feign;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.user.dto.LoginContextDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * user 服务 Feign 客户端 (auth 模块专用)
 *
 * <p>用于登录场景,加载用户密码/角色/权限等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-pmis-user", fallbackFactory = UserAuthClientFallback.class)
public interface UserAuthClient {

    /**
     * 根据用户名加载登录上下文
     *
     * @param username 用户名
     * @return 统一响应结果，包含登录上下文（密码、角色、权限等）
     */
    @GetMapping("/api/v1/feign/auth/context/by-username")
    Result<LoginContextDTO> getLoginContextByUsername(@RequestParam("username") String username);

    /**
     * 根据用户 ID 加载登录上下文
     *
     * @param userId 用户 ID
     * @return 统一响应结果，包含登录上下文（密码、角色、权限等）
     */
    @GetMapping("/api/v1/feign/auth/context/by-id")
    Result<LoginContextDTO> getLoginContextById(@RequestParam("userId") Long userId);
}
