package com.njydsz.nextwiki.server.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.entity.FileAcl;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.service.ShareDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件权限校验服务
 * <p>
 * 统一封装文件操作权限校验逻辑，在应用服务层调用。
 *
 * <p><b>权限模型：</b>
 * <ul>
 *   <li>文件所有者拥有全部权限</li>
 *   <li>非所有者需通过 ACL 校验</li>
 *   <li>分享文件需验证分享链接权限</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilePermissionService {

    private final FileNodeRepository fileNodeRepository;
    private final ShareDomainService shareDomainService;

    /** 权限位：读取 */
    public static final int PERM_READ = FileAcl.PERM_READ;
    /** 权限位：写入 */
    public static final int PERM_WRITE = FileAcl.PERM_WRITE;
    /** 权限位：删除 */
    public static final int PERM_DELETE = FileAcl.PERM_DELETE;
    /** 权限位：分享 */
    public static final int PERM_SHARE = FileAcl.PERM_SHARE;

    /**
     * 校验读取权限
     */
    public void checkRead(String nodeId, String userId) {
        checkPermission(nodeId, userId, PERM_READ, "读取");
    }

    /**
     * 校验写入权限
     */
    public void checkWrite(String nodeId, String userId) {
        checkPermission(nodeId, userId, PERM_WRITE, "写入");
    }

    /**
     * 校验删除权限
     */
    public void checkDelete(String nodeId, String userId) {
        checkPermission(nodeId, userId, PERM_DELETE, "删除");
    }

    /**
     * 校验分享权限
     */
    public void checkShare(String nodeId, String userId) {
        checkPermission(nodeId, userId, PERM_SHARE, "分享");
    }

    /**
     * 通用权限校验
     */
    public void checkPermission(String nodeId, String userId, int permission, String action) {
        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
        }

        // 文件所有者拥有全部权限
        if (userId.equals(node.getCreatedBy())) {
            return;
        }

        // ACL 权限校验（结果走缓存）
        List<FileAcl> acls = getEffectiveAcls(nodeId, userId);
        boolean hasPermission = false;
        for (FileAcl acl : acls) {
            if (acl.hasPermission(permission)) {
                hasPermission = true;
                break;
            }
        }
        if (!hasPermission) {
            log.warn("[FilePermissionService] 权限不足: userId={}, nodeId={}, action={}",
                    userId, nodeId, action);
            throw BusinessException.of(NextwikiExceptionCode.PERMISSION_DENIED)
                    .data("nodeId", nodeId)
                    .data("action", action);
        }
    }

    /**
     * 查询用户对文件节点的有效 ACL 列表（结果缓存）
     * <p>
     * 缓存名 {@code nextwiki:file:acl}，key 为 {@code fileNodeId:userId}。
     * 当 ACL 发生变更（授予/撤销）或配额发生变更时，由 {@code ShareDomainService} /
     * {@code QuotaDomainService} 通过 {@code @CacheEvict} 清除。
     *
     * @param fileNodeId 文件节点ID
     * @param userId     用户ID
     * @return 有效 ACL 列表，可能为空
     */
    @Cacheable(cacheNames = "nextwiki:file:acl",
            key = "#fileNodeId + ':' + #userId",
            condition = "#userId != null")
    public List<FileAcl> getEffectiveAcls(String fileNodeId, String userId) {
        return shareDomainService.checkPermissionAcls(fileNodeId, userId);
    }
}
