package com.njydsz.pmis.userinfo.feign;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.userinfo.dto.auth.LoginContextDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * UserAuthClient 降级工厂
 *
 * <p>user 服务不可用时返回 null,登录会失败但不影响流程。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class UserAuthClientFallback implements FallbackFactory<UserAuthClient> {

    /**
     * 创建降级代理
     *
     * @param cause 触发降级的异常
     * @return 降级后的 UserAuthClient 实例，所有方法返回服务不可用响应
     */
    @Override
    public UserAuthClient create(Throwable cause) {
        log.error("[Feign] user 服务降级: {}", cause == null ? "?" : cause.getMessage());
        return new UserAuthClient() {
            @Override
            public Result<LoginContextDTO> getLoginContextByUsername(String username) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<LoginContextDTO> getLoginContextById(String userId) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
