package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.njydsz.nextwiki.domain.entity.ShareLink;
import com.njydsz.nextwiki.domain.service.ShareDomainService;

/**
 * 分享应用服务。
 * <p>创建/校验/撤销分享链接。
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
     * @param password       访问密码（可为空，PUBLIC 类型忽略）
     * @param expireTime     过期时间（可为空表示永不过期）
     * @param maxAccessCount 最大访问次数（可为空表示不限）
     * @param userId         创建者 ID
     * @return 分享链接实体 {@link ShareLink}
     * @throws 由 {@link ShareDomainService} 在节点不存在/无权限时抛出的业务异常
     * @transaction {@code @Transactional(rollbackFor = Exception.class)}
     * @complexity O(1)（一次分享记录写入）
     * @note 委托 {@link ShareDomainService} 实现；创建后通常由事件/通知触达被分享方
     */
    @Transactional(rollbackFor = Exception.class)
    public ShareLink createShare(String fileNodeId, String shareType, String password,
                                 LocalDateTime expireTime, Integer maxAccessCount, String userId) {
        return shareDomainService.createShare(fileNodeId, shareType, password,
                expireTime, maxAccessCount, userId);
    }

    /**
     * 验证分享链接访问权限。
     * <p>校验分享码、提取码（如有）、访问密码，并判断过期时间与访问次数上限。
     *
     * @param shareCode   分享码（分享链接唯一标识）
     * @param extractCode 提取码（可为空；与分享码配合用于 LIMITED 类型）
     * @param password    访问密码（可为空；PUBLIC 类型忽略）
     * @return 分享链接实体 {@link ShareLink}；任一校验不通过返回 {@code null}
     * @complexity O(1)（一次分享记录查询 + 内存校验）
     * @note 无事务边界；返回 {@code null} 表示验证失败，由调用方决定提示
     */
    public ShareLink verifyAccess(String shareCode, String extractCode, String password) {
        return shareDomainService.verifyAccess(shareCode, extractCode, password);
    }

    /**
     * 撤销分享链接（使分享码立即失效）。
     *
     * @param shareId 分享 ID
     * @param userId  操作者 ID（需具备该分享的撤销权限）
     * @return 无返回值
     * @throws 由 {@link ShareDomainService} 在分享不存在/无权限时抛出的业务异常
     * @transaction {@code @Transactional(rollbackFor = Exception.class)}
     * @complexity O(1)（一次分享状态更新）
     * @note 撤销后原分享码不可再访问；已发出的访问不再有效
     */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(String shareId, String userId) {
        shareDomainService.revoke(shareId, userId);
    }

    /**
     * 查询某用户创建的全部分享链接列表。
     *
     * @param userId 用户 ID
     * @return 分享链接列表 {@link ShareLink}（可能为空，非 {@code null}）
     * @complexity O(1)（一次按用户查询）
     * @note 只读，无事务边界
     */
    public List<ShareLink> findByUserId(String userId) {
        return shareDomainService.findByUserId(userId);
    }
}
