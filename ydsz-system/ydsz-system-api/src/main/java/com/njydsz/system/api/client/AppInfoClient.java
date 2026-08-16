package com.njydsz.system.api.client;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.system.api.fallback.AppInfoClientFallback;

/**
 * 应用密钥校验 Feign 客户端（供跨服务调用）。
 *
 * <p>提供 OAuth2 应用 {@code client_id / client_secret} 的远程校验能力（BCrypt）。
 * 典型场景：网关 / 用户中心校验第三方应用凭证换取访问令牌。
 *
 * <p><b>安全约定：</b>密钥通过 <b>POST body</b> 传输，严禁出现在 URL 中。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = FeignClientConstants.SYSTEM, contextId = "appInfoClient",
        fallbackFactory = AppInfoClientFallback.class)
public interface AppInfoClient {

    /**
     * 校验应用密钥（走 BCrypt）。
     *
     * @param request 请求体（必须包含 {@code appKey} 和 {@code appSecret} 字段）
     * @return 校验通过返回 {@code true}；应用不存在 / 未启用 / 密钥不匹配返回 {@code false}
     */
    @PostMapping(FeignClientConstants.SYSTEM_PATH_APP_VALIDATE)
    BaseResponse<Boolean> validateClient(@RequestBody Map<String, String> request);
}
