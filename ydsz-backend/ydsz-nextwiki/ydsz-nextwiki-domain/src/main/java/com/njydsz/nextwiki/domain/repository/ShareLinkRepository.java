package com.njydsz.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.nextwiki.domain.entity.ShareLink;

/**
 * 分享链接仓储接口
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ShareLinkRepository {

    ShareLink save(ShareLink shareLink);

    ShareLink findById(String id);

    ShareLink findByShareCode(String shareCode);

    List<ShareLink> findByFileNodeId(String fileNodeId);

    List<ShareLink> findActiveSharesByUserId(String userId);

    void update(ShareLink shareLink);

    void revoke(String id);

    void incrementAccessCount(String id);
}
