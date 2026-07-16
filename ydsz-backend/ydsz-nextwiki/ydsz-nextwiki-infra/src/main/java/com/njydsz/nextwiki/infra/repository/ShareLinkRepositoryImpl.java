package com.njydsz.nextwiki.infra.repository;

import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.njydsz.nextwiki.domain.entity.ShareLink;
import com.njydsz.nextwiki.domain.repository.ShareLinkRepository;
import com.njydsz.nextwiki.infra.mapper.ShareLinkMapper;

import lombok.RequiredArgsConstructor;

/**
 * 分享链接仓储实现
 *
 * @author ydsz-team
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
        if (shareLink.getRevision() == null) {
            // 兜底：未携带 revision 时退化为普通更新，避免业务阻断
            shareLinkMapper.updateById(shareLink);
            return;
        }
        int affected = shareLinkMapper.updateWithRevision(shareLink);
        if (affected == 0) {
            throw new OptimisticLockingFailureException(
                    "ShareLink 乐观锁更新失败，id=" + shareLink.getId()
                            + ", revision=" + shareLink.getRevision());
        }
        shareLink.setRevision(shareLink.getRevision() + 1);
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
