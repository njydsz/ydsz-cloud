package com.njydsz.nextwiki.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.entity.FileAcl;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.entity.ShareLink;
import com.njydsz.nextwiki.domain.enums.NextwikiEnums.ShareStatus;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.nextwiki.domain.repository.FileAclRepository;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.ShareLinkRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分享与 ACL 领域服务
 * <p>
 * 管理文件分享链接的创建、访问验证、撤销，以及文件级 ACL 权限的授予和校验。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareDomainService {

    private final ShareLinkRepository shareLinkRepository;
    private final FileAclRepository fileAclRepository;
    private final FileNodeRepository fileNodeRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 创建分享链接
     */
    @Transactional(rollbackFor = Exception.class)
    public ShareLink createShare(String fileNodeId, String shareType, String password,
                                  LocalDateTime expireTime, Integer maxAccessCount, String userId) {
        FileNode fileNode = fileNodeRepository.findById(fileNodeId);
        if (fileNode == null) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("fileNodeId", fileNodeId);
        }

        // 生成分享码和提取码
        String shareCode = UUID.randomUUID().toString().replace("-", "");
        String extractCode = generateExtractCode();

        ShareLink shareLink = ShareLink.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .fileNodeId(fileNodeId)
                .shareCode(shareCode)
                .extractCode(extractCode)
                .shareType(shareType)
                .expireTime(expireTime)
                .maxAccessCount(maxAccessCount)
                .accessCount(0)
                .status(ShareStatus.ACTIVE.getCode())
                .password(password != null && !password.isEmpty() ? passwordEncoder.encode(password) : null)
                .revision(0)
                .deleted(0)
                .build();

        shareLink.setCreatedBy(userId);
        shareLink.setCreatedAt(LocalDateTime.now());
        shareLink.setUpdatedBy(userId);
        shareLink.setUpdatedAt(LocalDateTime.now());

        ShareLink saved = shareLinkRepository.save(shareLink);

        // 更新文件节点的共享状态
        fileNode.setShareStatus("shared");
        fileNode.setUpdatedBy(userId);
        fileNode.setUpdatedAt(LocalDateTime.now());
        fileNodeRepository.update(fileNode);

        eventPublisher.publishEvent(FileOperatedEvent.builder()
                .operation(FileOperatedEvent.OP_SHARE)
                .fileNodeId(fileNodeId)
                .fileName(fileNode.getName())
                .nodeType(fileNode.getNodeType())
                .operatorId(userId)
                .operatedAt(LocalDateTime.now())
                .extra(shareCode)
                .build());

        log.info("[ShareDomainService] 创建分享: fileNodeId={}, shareCode={}", fileNodeId, shareCode);
        return saved;
    }

    /**
     * 验证分享链接访问
     */
    public ShareLink verifyAccess(String shareCode, String extractCode, String password) {
        ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode);
        if (shareLink == null) {
            throw new BusinessException(NextwikiExceptionCode.SHARE_NOT_FOUND);
        }

        ShareStatus currentStatus = ShareStatus.fromCode(shareLink.getStatus());
        if (currentStatus == null || currentStatus.isTerminal()) {
            // 非 ACTIVE 状态（已过期/已撤销）视为已失效
            throw new BusinessException(NextwikiExceptionCode.SHARE_EXPIRED);
        }

        // 检查过期时间
        if (shareLink.getExpireTime() != null && shareLink.getExpireTime().isBefore(LocalDateTime.now())) {
            if (currentStatus.canTransitTo(ShareStatus.EXPIRED)) {
                shareLink.setStatus(ShareStatus.EXPIRED.getCode());
                shareLinkRepository.update(shareLink);
            }
            throw new BusinessException(NextwikiExceptionCode.SHARE_EXPIRED);
        }

        // 检查访问次数
        if (shareLink.getMaxAccessCount() != null
                && shareLink.getAccessCount() != null
                && shareLink.getAccessCount() >= shareLink.getMaxAccessCount()) {
            throw new BusinessException(NextwikiExceptionCode.SHARE_ACCESS_LIMIT);
        }

        // 验证提取码
        if (shareLink.getExtractCode() != null && !shareLink.getExtractCode().equals(extractCode)) {
            throw new BusinessException(NextwikiExceptionCode.SHARE_EXTRACT_CODE_ERROR);
        }

        // 验证密码
        if (shareLink.getPassword() != null && !shareLink.getPassword().isEmpty()) {
            if (password == null || !passwordEncoder.matches(password, shareLink.getPassword())) {
                throw new BusinessException(NextwikiExceptionCode.SHARE_PASSWORD_ERROR);
            }
        }

        // 增加访问次数
        shareLinkRepository.incrementAccessCount(shareLink.getId());

        return shareLink;
    }

    /**
     * 撤销分享
     */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(String shareId, String userId) {
        ShareLink shareLink = shareLinkRepository.findById(shareId);
        if (shareLink == null) {
            throw new BusinessException(NextwikiExceptionCode.SHARE_NOT_FOUND);
        }
        shareLinkRepository.revoke(shareId);
        log.info("[ShareDomainService] 撤销分享: shareId={}, userId={}", shareId, userId);
    }

    /**
     * 授予文件 ACL 权限
     * <p>
     * 授予后清除 {@code nextwiki:file:acl} 缓存中该 (fileNodeId, granteeId) 维度的条目，
     * 避免校验逻辑继续读到旧的 ACL 列表。{@code allEntries = true} 是因为 ACL 变更可能
     * 影响多个用户对该节点的有效权限（例如继承传播），简单全量清除最稳妥。
     */
    @CacheEvict(cacheNames = "nextwiki:file:acl", allEntries = true)
    public FileAcl grantPermission(String fileNodeId, String granteeType, String granteeId,
                                    int permissionMask, String userId) {
        FileAcl acl = FileAcl.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .fileNodeId(fileNodeId)
                .granteeType(granteeType)
                .granteeId(granteeId)
                .permissionMask(permissionMask)
                .inherited(false)
                .isOwner(false)
                .revision(0)
                .deleted(0)
                .build();

        acl.setCreatedBy(userId);
        acl.setCreatedAt(LocalDateTime.now());
        acl.setUpdatedBy(userId);
        acl.setUpdatedAt(LocalDateTime.now());

        FileAcl saved = fileAclRepository.save(acl);
        log.info("[ShareDomainService] 授予权限: fileNodeId={}, granteeType={}, granteeId={}, mask={}",
                fileNodeId, granteeType, granteeId, permissionMask);
        return saved;
    }

    /**
     * 检查用户是否拥有指定权限
     */
    public boolean checkPermission(String fileNodeId, String userId, List<String> roleIds, int permission) {
        // 查询文件所有者
        FileNode fileNode = fileNodeRepository.findById(fileNodeId);
        if (fileNode != null && userId.equals(fileNode.getCreatedBy())) {
            return true;
        }

        // 查询 ACL
        List<FileAcl> acls = fileAclRepository.findEffectivePermissions(fileNodeId, userId, roleIds);
        for (FileAcl acl : acls) {
            if (acl.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查询用户对文件节点的有效 ACL 列表（不含所有者短路判断）
     * <p>
     * 该方法仅做数据库查询，缓存由调用方 {@code FilePermissionService.getEffectiveAcls} 通过
     * {@code @Cacheable} 控制。这里保持纯粹的领域查询职责。
     *
     * @param fileNodeId 文件节点ID
     * @param userId     用户ID
     * @return 有效 ACL 列表（含继承），可能为空
     */
    public List<FileAcl> checkPermissionAcls(String fileNodeId, String userId) {
        return fileAclRepository.findEffectivePermissions(fileNodeId, userId, List.of());
    }

    /**
     * 查询用户的分享列表
     */
    public List<ShareLink> findByUserId(String userId) {
        return shareLinkRepository.findActiveSharesByUserId(userId);
    }

    /**
     * 设置文件所有者
     */
    public FileAcl setOwner(String fileNodeId, String userId) {
        return grantPermission(fileNodeId, "user", userId, FileAcl.PERM_ALL, userId);
    }

    // ==================== 私有方法 ====================

    /**
     * 生成 4 位数字提取码
     */
    private String generateExtractCode() {
        int code = (int) (Math.random() * 9000) + 1000;
        return String.valueOf(code);
    }
}
