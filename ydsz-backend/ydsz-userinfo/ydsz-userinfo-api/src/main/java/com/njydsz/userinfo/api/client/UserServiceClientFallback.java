package com.njydsz.userinfo.api.client;

import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

import lombok.extern.slf4j.Slf4j;

/**
 * UserServiceClient 降级处理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public BaseResponse<UserAccountVO> getUserInfo(String userId) {
        log.warn("UserServiceClient fallback: getUserInfo={}", userId);
        return BaseResponse.error("服务降级：用户信息查询不可用");
    }
}
