package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.nextwiki.domain.entity.ShareLink;
import com.njydsz.nextwiki.domain.service.ShareDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分享应用服务
 * <p>
 * 编排文件分享相关操作，协调领域服务。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareApplicationService {

    private final ShareDomainService shareDomainService;

    @Transactional(rollbackFor = Exception.class)
    public ShareLink createShare(String fileNodeId, String shareType, String password,
                                 LocalDateTime expireTime, Integer maxAccessCount, String userId) {
        return shareDomainService.createShare(fileNodeId, shareType, password,
                expireTime, maxAccessCount, userId);
    }

    public ShareLink verifyAccess(String shareCode, String extractCode, String password) {
        return shareDomainService.verifyAccess(shareCode, extractCode, password);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(String shareId, String userId) {
        shareDomainService.revoke(shareId, userId);
    }

    public List<ShareLink> findByUserId(String userId) {
        return shareDomainService.findByUserId(userId);
    }
}
