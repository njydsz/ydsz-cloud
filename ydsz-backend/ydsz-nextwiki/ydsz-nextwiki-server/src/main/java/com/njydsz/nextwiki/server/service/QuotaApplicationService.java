package com.njydsz.nextwiki.server.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.nextwiki.domain.entity.StorageQuota;
import com.njydsz.nextwiki.domain.service.QuotaDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 配额应用服务。
 * <p>对外暴露配额查询/扣减/退还 API。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaApplicationService {

    private final QuotaDomainService quotaDomainService;

    public StorageQuota getQuotaInfo(String scopeType, String scopeId) {
        return quotaDomainService.getQuotaInfo(scopeType, scopeId);
    }

    @Transactional(rollbackFor = Exception.class)
    public StorageQuota setQuota(String scopeType, String scopeId, Long quotaLimit,
                                 Integer fileCountLimit, String userId) {
        return quotaDomainService.setQuota(scopeType, scopeId, quotaLimit, fileCountLimit, userId);
    }
}
