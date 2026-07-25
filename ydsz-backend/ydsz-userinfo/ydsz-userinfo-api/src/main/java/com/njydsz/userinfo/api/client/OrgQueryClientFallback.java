package com.njydsz.userinfo.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OrgQueryClient fallback.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class OrgQueryClientFallback implements OrgQueryClient {

    @Override
    public Object queryUserById(String userId) {
        log.warn("OrgQueryClient fallback: queryUserById={}", userId);
        return null;
    }

    @Override
    public Object getDeptTree() {
        log.warn("OrgQueryClient fallback: getDeptTree");
        return null;
    }
}
