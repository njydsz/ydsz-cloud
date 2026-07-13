package com.njydsz.pmis.nextwiki.infra.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.njydsz.pmis.nextwiki.domain.entity.ShareLink;
import com.njydsz.pmis.nextwiki.domain.repository.ShareLinkRepository;
import com.njydsz.pmis.nextwiki.infra.mapper.ShareLinkMapper;

import lombok.RequiredArgsConstructor;

/**
 * 分享链接仓储实现
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Repository
@RequiredArgsConstructor
public class ShareLinkRepositoryImpl implements ShareLinkRepository {

    private final ShareLinkMapper shareLinkMapper;

    @Override
    public ShareLink save(ShareLink shareLink) {
        shareLinkMapper.insert(shareLink);
        return shareLink;
    }

    @Override
    public ShareLink findById(String id) {
        return shareLinkMapper.selectById(id);
    }

    @Override
    public ShareLink findByShareCode(String shareCode) {
        return shareLinkMapper.selectByShareCode(shareCode);
    }

    @Override
    public List<ShareLink> findByFileNodeId(String fileNodeId) {
        return shareLinkMapper.selectByFileNodeId(fileNodeId);
    }

    @Override
    public List<ShareLink> findActiveSharesByUserId(String userId) {
        return shareLinkMapper.selectActiveSharesByUserId(userId);
    }

    @Override
    public void update(ShareLink shareLink) {
        shareLinkMapper.updateById(shareLink);
    }

    @Override
    public void revoke(String id) {
        shareLinkMapper.revoke(id);
    }

    @Override
    public void incrementAccessCount(String id) {
        shareLinkMapper.incrementAccessCount(id);
    }
}
