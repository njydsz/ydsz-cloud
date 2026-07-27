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
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareApplicationService {

    /** 分享领域服务 */
    private final ShareDomainService shareDomainService;

    /**
     * 创建文件分享链接。
     *
     * @param fileNodeId     文件节点 ID
     * @param shareType      分享类型（PUBLIC/PASSWORD/LIMITED）
     * @param password       访问密码（可为空）
     * @param expireTime     过期时间（可为空表示永不过期）
     * @param maxAccessCount 最大访问次数（可为空表示不限）
     * @param userId         创建者 ID
     * @return 分享链接实体
     */
    @Transactional(rollbackFor = Exception.class)
    public ShareLink createShare(String fileNodeId, String shareType, String password,
                                 LocalDateTime expireTime, Integer maxAccessCount, String userId) {
        return shareDomainService.createShare(fileNodeId, shareType, password,
                expireTime, maxAccessCount, userId);
    }

    /**
     * 验证分享链接访问权限。
     *
     * @param shareCode   分享码
     * @param extractCode 提取码（可为空）
     * @param password    访问密码（可为空）
     * @return 分享链接实体，验证失败返回 null
     */
    public ShareLink verifyAccess(String shareCode, String extractCode, String password) {
        return shareDomainService.verifyAccess(shareCode, extractCode, password);
    }

    /**
     * 撤销分享链接。
     *
     * @param shareId 分享 ID
     * @param userId  操作者 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(String shareId, String userId) {
        shareDomainService.revoke(shareId, userId);
    }

    /**
     * 查询用户创建的分享链接列表。
     *
     * @param userId 用户 ID
     * @return 分享链接列表
     */
    public List<ShareLink> findByUserId(String userId) {
        return shareDomainService.findByUserId(userId);
    }
}
