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
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class ShareLinkRepositoryImpl implements ShareLinkRepository {

    private final ShareLinkMapper shareLinkMapper;

    /**
     * 插入新建的分享链接记录（初次创建分享时调用）。
     *
     * @param shareLink 待持久化的分享链接实体（含 fileNodeId、分享码、类型、过期等）
     * @return 已落库的分享链接实体（含自增主键）
     */
    @Override
    public ShareLink save(ShareLink shareLink) {
        shareLinkMapper.insert(shareLink);
        return shareLink;
    }

    /**
     * 按主键查询分享链接。
     *
     * @param id 分享链接主键
     * @return 分享链接实体；不存在则返回 null
     */
    @Override
    public ShareLink findById(String id) {
        return shareLinkMapper.selectById(id);
    }

    /**
     * 按分享码查询分享链接，是外部用户通过分享入口访问文件的鉴权入口。
     *
     * @param shareCode 分享码（对外暴露的唯一标识）
     * @return 命中的分享链接实体；不存在则返回 null
     */
    @Override
    public ShareLink findByShareCode(String shareCode) {
        return shareLinkMapper.selectByShareCode(shareCode);
    }

    /**
     * 查询指定文件节点下创建的全部分享链接（含有效与失效）。
     *
     * @param fileNodeId 文件节点 ID
     * @return 分享链接列表
     */
    @Override
    public List<ShareLink> findByFileNodeId(String fileNodeId) {
        return shareLinkMapper.selectByFileNodeId(fileNodeId);
    }

    /**
     * 查询某用户创建的、当前仍有效的分享链接列表（未过期且未撤销），用于"我的分享"管理页。
     *
     * @param userId 分享创建人 ID
     * @return 有效分享链接列表
     */
    @Override
    public List<ShareLink> findActiveSharesByUserId(String userId) {
        return shareLinkMapper.selectActiveSharesByUserId(userId);
    }

    /**
     * 乐观锁更新分享链接；未携带 revision 时退化为普通更新，受影响行数为 0 抛出
     * {@link OptimisticLockingFailureException}，成功后 revision 自增 1。
     *
     * @param shareLink 待更新的分享链接实体（必须携带 id）
     */
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

    /**
     * 撤销分享链接（使其立即失效，外部访问被拒绝），用于主动终止分享。
     *
     * @param id 分享链接主键
     */
    @Override
    public void revoke(String id) {
        shareLinkMapper.revoke(id);
    }

    /**
     * 分享链接被访问时访问计数 +1，用于统计热度与 maxAccessCount 限流判定。
     *
     * @param id 分享链接主键
     */
    @Override
    public void incrementAccessCount(String id) {
        shareLinkMapper.incrementAccessCount(id);
    }
}
