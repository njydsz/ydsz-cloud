package com.njydsz.nextwiki.domain.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.entity.FileAcl;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.entity.ShareLink;
import com.njydsz.nextwiki.domain.enums.NextwikiEnums.ShareStatus;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.nextwiki.domain.repository.FileAclRepository;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.ShareLinkRepository;

/**
 * NextWiki 分享领域服务。
 * <p>分享链接生成、权限校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Service
@RequiredArgsConstructor
public class ShareDomainService {

    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final ShareLinkRepository shareLinkRepository;
    private final FileAclRepository fileAclRepository;
    private final FileNodeRepository fileNodeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisStringOps stringOps;

    private final BCryptPasswordEncoder passwordEncoder;

    /** P0-4: 防暴力破解配置 */
    private static final String KEY_SHARE_FAIL = "nextwiki:share:fail:";
    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_DURATION_MINUTES = 30;

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
        String shareCode = String.valueOf(snowflakeIdGenerator.nextId()).replace("-", "");
        String extractCode = generateExtractCode();

        ShareLink shareLink = ShareLink.builder()
                .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
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
        shareLink.setUpdatedBy(userId);

        ShareLink saved = shareLinkRepository.save(shareLink);

        // 更新文件节点的共享状态
        fileNode.setShareStatus("shared");
        fileNode.setUpdatedBy(userId);
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
        // P0-4: 防暴力破解——检查失败次数是否超限
        String failKey = KEY_SHARE_FAIL + shareCode;
        String failCountStr = stringOps.get(failKey, String.class);
        if (failCountStr != null && Integer.parseInt(failCountStr) >= MAX_FAIL_COUNT) {
            log.warn("[ShareDomainService] 分享链接已被临时锁定: shareCode={}", shareCode);
            throw new BusinessException(NextwikiExceptionCode.SHARE_LOCKED);
        }

        ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode);
        if (shareLink == null) {
            throw new BusinessException(NextwikiExceptionCode.SHARE_NOT_FOUND);
        }

        ShareStatus currentStatus = ShareStatus.fromCode(shareLink.getStatus());
        if (currentStatus == null || currentStatus.isTerminal()) {
            throw new BusinessException(NextwikiExceptionCode.SHARE_EXPIRED);
        }

        if (shareLink.getExpireTime() != null && shareLink.getExpireTime().isBefore(LocalDateTime.now())) {
            if (currentStatus.canTransitTo(ShareStatus.EXPIRED)) {
                shareLink.setStatus(ShareStatus.EXPIRED.getCode());
                shareLinkRepository.update(shareLink);
            }
            throw new BusinessException(NextwikiExceptionCode.SHARE_EXPIRED);
        }

        if (shareLink.getMaxAccessCount() != null
                && shareLink.getAccessCount() != null
                && shareLink.getAccessCount() >= shareLink.getMaxAccessCount()) {
            throw new BusinessException(NextwikiExceptionCode.SHARE_ACCESS_LIMIT);
        }

        boolean verifyFailed = false;

        if (shareLink.getExtractCode() != null && !shareLink.getExtractCode().equals(extractCode)) {
            verifyFailed = true;
        }

        if (!verifyFailed && shareLink.getPassword() != null && !shareLink.getPassword().isEmpty()) {
            if (password == null || !passwordEncoder.matches(password, shareLink.getPassword())) {
                verifyFailed = true;
            }
        }

        if (verifyFailed) {
            // 记录失败次数
            Long failCount = stringOps.incr(failKey, 1);
            if (failCount != null && failCount == 1) {
                stringOps.expire(failKey, Duration.ofMinutes(LOCK_DURATION_MINUTES));
            }
            log.warn("[ShareDomainService] 验证失败: shareCode={}, failCount={}", shareCode, failCount);
            throw new BusinessException(
                    shareLink.getExtractCode() != null
                            ? NextwikiExceptionCode.SHARE_EXTRACT_CODE_ERROR
                            : NextwikiExceptionCode.SHARE_PASSWORD_ERROR);
        }

        // 验证成功，清除失败计数
        stringOps.del(failKey);

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
    @CacheEvict(cacheNames = CacheConstants.NEXTWIKI_FILE_ACL_CACHE, allEntries = true)
    public FileAcl grantPermission(String fileNodeId, String granteeType, String granteeId,
                                    int permissionMask, String userId) {
        FileAcl acl = FileAcl.builder()
                .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
                .fileNodeId(fileNodeId)
                .granteeType(granteeType)
                .granteeId(granteeId)
                .permissionMask(permissionMask)
                .inherited(false)
                .owner(false)
                .revision(0)
                .deleted(0)
                .build();

        acl.setCreatedBy(userId);
        acl.setUpdatedBy(userId);

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
