package com.njydsz.pmis.auth.feign;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.user.dto.LoginContextDTO;
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

    @Override
    public UserAuthClient create(Throwable cause) {
        log.error("[Feign] user 服务降级: {}", cause == null ? "?" : cause.getMessage());
        return new UserAuthClient() {
            @Override
            public R<LoginContextDTO> getLoginContextByUsername(String username) {
                return R.failed(com.njydsz.pmis.common.api.BizErrorCode.SERVICE_UNAVAILABLE,
                        "用户服务不可用，请稍后重试");
            }

            @Override
            public R<LoginContextDTO> getLoginContextById(Long userId) {
                return R.failed(com.njydsz.pmis.common.api.BizErrorCode.SERVICE_UNAVAILABLE,
                        "用户服务不可用，请稍后重试");
            }
        };
    }
}
