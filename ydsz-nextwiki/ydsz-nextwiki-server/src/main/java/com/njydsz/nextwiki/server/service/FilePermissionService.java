package com.njydsz.nextwiki.server.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.dto.FileAclDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.query.FileAclQuery;
import com.njydsz.nextwiki.domain.repository.FileAclRepository;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.service.FilePermissionDomainService;
import com.njydsz.nextwiki.domain.vo.FileAclVO;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;

/**
 * 文件权限校验服务
 *
 * <p>统一封装文件操作权限校验逻辑，在应用服务层调用。
 *
 * <p><b>权限模型：</b>
 *
 * <ul>
 *   <li>文件所有者拥有全部权限
 *   <li>非所有者需通过 ACL 校验
 *   <li>分享文件需验证分享链接权限
 * </ul>
 *
 * <p>缓存控制（{@code @Cacheable}/{@code @CacheEvict}）统一在 server 层管理，领域层保持纯粹。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilePermissionService {

  private final FileNodeRepository fileNodeRepository;
  private final FileAclRepository fileAclRepository;
  private final FilePermissionDomainService filePermissionDomainService;

  /** 权限位：读取 */
  public static final int PERM_READ = FilePermissionDomainService.PERM_READ;

  /** 权限位：写入 */
  public static final int PERM_WRITE = FilePermissionDomainService.PERM_WRITE;

  /** 权限位：删除 */
  public static final int PERM_DELETE = FilePermissionDomainService.PERM_DELETE;

  /** 权限位：分享 */
  public static final int PERM_SHARE = FilePermissionDomainService.PERM_SHARE;

  /** 权限位：下载 */
  public static final int PERM_DOWNLOAD = FilePermissionDomainService.PERM_DOWNLOAD;

  /**
   * 校验读取权限（{@link #PERM_READ}）。
   *
   * @param nodeId 文件节点 ID
   * @param userId 用户 ID
   * @throws BusinessException 无权限（PERMISSION_DENIED）或节点不存在时抛出
   */
  public void checkRead(String nodeId, String userId) {
    checkPermission(nodeId, userId, PERM_READ, "读取");
  }

  /**
   * 校验写入权限（{@link #PERM_WRITE}，含移动/重命名/编辑内容）。
   *
   * @param nodeId 文件节点 ID
   * @param userId 用户 ID
   * @throws BusinessException 无权限或节点不存在时抛出
   */
  public void checkWrite(String nodeId, String userId) {
    checkPermission(nodeId, userId, PERM_WRITE, "写入");
  }

  /**
   * 校验删除权限（{@link #PERM_DELETE}）。
   *
   * @param nodeId 文件节点 ID
   * @param userId 用户 ID
   * @throws BusinessException 无权限或节点不存在时抛出
   */
  public void checkDelete(String nodeId, String userId) {
    checkPermission(nodeId, userId, PERM_DELETE, "删除");
  }

  /**
   * 校验分享权限（{@link #PERM_SHARE}）。
   *
   * @param nodeId 文件节点 ID
   * @param userId 用户 ID
   * @throws BusinessException 无权限或节点不存在时抛出
   */
  public void checkShare(String nodeId, String userId) {
    checkPermission(nodeId, userId, PERM_SHARE, "分享");
  }

  /**
   * 通用权限校验（所有者全权 + 非所有者走 ACL 位运算）。
   *
   * <p>所有者（{@code createdBy == userId}）直接放行；否则遍历有效 ACL 列表 （{@link
   * #getEffectiveAcls}，结果走缓存），任一含目标权限位即通过，否则抛 {@code PERMISSION_DENIED}。
   *
   * @param nodeId 文件节点 ID
   * @param userId 用户 ID
   * @param permission 目标权限位
   * @param action 动作名（用于错误日志与提示，如"读取"）
   * @throws BusinessException 节点不存在（FILE_NOT_FOUND）或无权限（PERMISSION_DENIED）时抛出
   */
  public void checkPermission(String nodeId, String userId, int permission, String action) {
    FileNodeVO node = fileNodeRepository.findById(nodeId).orElse(null);
    if (node == null) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
    }

    // 文件所有者拥有全部权限
    if (userId.equals(node.getCreatedBy())) {
      return;
    }

    // ACL 权限校验（结果走缓存）
    List<FileAclVO> acls = getEffectiveAcls(nodeId, userId);
    boolean hasPermission = false;
    for (FileAclVO acl : acls) {
      if (acl.getPermissionMask() != null && (acl.getPermissionMask() & permission) == permission) {
        hasPermission = true;
        break;
      }
    }
    if (!hasPermission) {
      log.warn(
          "[FilePermissionService] 权限不足: userId={}, nodeId={}, action={}", userId, nodeId, action);
      throw BusinessException.of(NextwikiExceptionCode.PERMISSION_DENIED)
          .data("nodeId", nodeId)
          .data("action", action);
    }
  }

  /**
   * 查询用户对文件节点的有效 ACL 列表（结果缓存）。
   *
   * <p>缓存名 {@code nextwiki:file:acl}，key 为 {@code fileNodeId:userId}。 当 ACL 发生变更（授予/撤销）时，由
   * {@link #grantPermission} 通过 {@code @CacheEvict(allEntries = true)} 清除。
   *
   * @param fileNodeId 文件节点 ID
   * @param userId 用户 ID
   * @return 有效 ACL 列表，可能为空
   */
  @Cacheable(
      cacheNames = CacheConstants.NEXTWIKI_FILE_ACL_CACHE,
      key = "#fileNodeId + ':' + #userId",
      condition = "#userId != null")
  public List<FileAclVO> getEffectiveAcls(String fileNodeId, String userId) {
    return fileAclRepository.findEffectivePermissions(
        FileAclQuery.builder()
            .fileNodeId(fileNodeId)
            .userId(userId)
            .build());
  }

  /**
   * 授予文件 ACL 权限（带缓存清除）。
   *
   * @param fileNodeId 文件节点 ID
   * @param granteeType 授权对象类型（user/role）
   * @param granteeId 授权对象 ID
   * @param permissionMask 权限位掩码
   * @param userId 操作者 ID
   * @return 创建的 ACL VO
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   */
  @Transactional(rollbackFor = Exception.class)
  @CacheEvict(cacheNames = CacheConstants.NEXTWIKI_FILE_ACL_CACHE, allEntries = true)
  public FileAclVO grantPermission(
      String fileNodeId,
      String granteeType,
      String granteeId,
      int permissionMask,
      String userId) {
    FileAclDTO dto = filePermissionDomainService.buildAcl(
        fileNodeId, granteeType, granteeId, permissionMask, userId);
    return fileAclRepository.save(dto);
  }

  /**
   * 设置文件所有者（带缓存清除）。
   *
   * @param fileNodeId 文件节点 ID
   * @param userId 所有者用户 ID
   * @return 创建的 ACL VO
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   */
  @Transactional(rollbackFor = Exception.class)
  @CacheEvict(cacheNames = CacheConstants.NEXTWIKI_FILE_ACL_CACHE, allEntries = true)
  public FileAclVO setOwner(String fileNodeId, String userId) {
    FileAclDTO dto = filePermissionDomainService.buildOwnerAcl(fileNodeId, userId);
    return fileAclRepository.save(dto);
  }
}
