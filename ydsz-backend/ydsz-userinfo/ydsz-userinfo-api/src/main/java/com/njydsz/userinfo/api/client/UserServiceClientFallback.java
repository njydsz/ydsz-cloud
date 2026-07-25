package com.njydsz.userinfo.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * UserServiceClient fallback.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public Object getUserInfo(String userId) {
        log.warn("UserServiceClient fallback: getUserInfo={}", userId);
        return null;
    }
}
