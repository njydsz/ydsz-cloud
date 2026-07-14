package com.njydsz.pmis.nextwiki.server.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.nextwiki.domain.entity.FileAcl;
import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.pmis.nextwiki.domain.service.ShareDomainService;

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
 * @author ydsz-pmis-team
 * @since 1.4.0
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
            throw BusinessException.builder().key("文件节点不存在: " + nodeId).build();
        }

        // 文件所有者拥有全部权限
        if (userId.equals(node.getCreatedBy())) {
            return;
        }

        // ACL 权限校验
        boolean hasPermission = shareDomainService.checkPermission(
                nodeId, userId, List.of(), permission);
        if (!hasPermission) {
            log.warn("[FilePermissionService] 权限不足: userId={}, nodeId={}, action={}",
                    userId, nodeId, action);
            throw BusinessException.builder().key(
                    "无" + action + "权限: " + nodeId).build();
        }
    }
}
