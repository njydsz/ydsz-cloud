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

    /**
     * 保存分享链接（新增或更新）。
     *
     * @param shareLink 待持久化的分享链接实体（含分享码、提取码、过期时间等）
     * @return 持久化后的分享链接（回填主键）
     */
    ShareLink save(ShareLink shareLink);

    /**
     * 按 ID 查询分享链接。
     *
     * @param id 分享链接 ID
     * @return 分享链接实体，不存在时返回 null
     */
    ShareLink findById(String id);

    /**
     * 按分享码（URL 唯一标识）查询分享链接，用于访客访问校验。
     *
     * @param shareCode 分享码（UUID）
     * @return 分享链接实体，不存在时返回 null
     */
    ShareLink findByShareCode(String shareCode);

    /**
     * 查询某文件节点关联的全部分享链接。
     *
     * @param fileNodeId 文件节点 ID
     * @return 分享链接列表，无记录时返回空列表
     */
    List<ShareLink> findByFileNodeId(String fileNodeId);

    /**
     * 查询某用户的全部有效分享链接（用于"我的分享"列表）。
     *
     * @param userId 用户 ID（创建人）
     * @return 有效分享链接列表，无记录时返回空列表
     */
    List<ShareLink> findActiveSharesByUserId(String userId);

    /**
     * 更新分享链接（如修改过期时间、访问上限等）。
     *
     * @param shareLink 待更新的分享链接实体（需含主键）
     */
    void update(ShareLink shareLink);

    /**
     * 撤销分享（将状态置为 revoked，使链接立即失效）。
     *
     * @param id 分享链接 ID
     */
    void revoke(String id);

    /**
     * 原子递增分享链接的已访问次数（每次成功访问后调用）。
     *
     * @param id 分享链接 ID
     */
    void incrementAccessCount(String id);
}
