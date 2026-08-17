package com.njydsz.nextwiki.domain.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.entity.FileAcl;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.repository.FileAclRepository;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;

/**
 * 文件权限领域服务。
 *
 * <p>管理文件 ACL（访问控制列表）的授予、校验、继承计算。
 *
 * <p><b>权限模型：</b>
 *
 * <ul>
 *   <li>文件所有者拥有全部权限（createdBy == userId 时直接放行）
 *   <li>非所有者需通过 ACL 校验（位运算校验权限）
 *   <li>支持权限继承（子节点可继承父节点权限）
 * </ul>
 *
 * <p><b>注意：</b>本服务仅负责领域逻辑，缓存控制由应用服务层 {@code FilePermissionService} 通过
 * {@code @Cacheable} / {@code @CacheEvict} 管理，保持领域层纯洁性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilePermissionDomainService {

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final FileAclRepository fileAclRepository;
  private final FileNodeRepository fileNodeRepository;

  /**
   * 授予文件 ACL 权限。
   *
   * <p><b>注意：</b>调用方需在应用服务层通过 {@code @CacheEvict} 清除 ACL 缓存，
 * 避免校验逻辑读到旧的 ACL 列表。
   *
   * @param fileNodeId    文件节点 ID
   * @param granteeType   授权对象类型（user/role/group/tenant）
   * @param granteeId     授权对象 ID
   * @param permissionMask 权限掩码（位运算组合）
   * @param userId        操作人 ID
   * @return 创建的 ACL 实体
   */
  @Transactional(rollbackFor = Exception.class)
  public FileAcl grantPermission(
      String fileNodeId, String granteeType, String granteeId, int permissionMask, String userId) {
    FileAcl acl =
        FileAcl.builder()
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
    log.info(
        "[FilePermissionDomainService] 授予权限: fileNodeId={}, granteeType={}, granteeId={}, mask={}",
        fileNodeId,
        granteeType,
        granteeId,
        permissionMask);
    return saved;
  }

  /**
   * 设置文件所有者（授予全部权限）。
   *
   * @param fileNodeId 文件节点 ID
   * @param userId     用户 ID
   * @return 创建的 ACL 实体
   */
  public FileAcl setOwner(String fileNodeId, String userId) {
    return grantPermission(fileNodeId, "user", userId, FileAcl.PERM_ALL, userId);
  }

  /**
   * 检查用户是否拥有指定权限（领域逻辑，不含缓存）。
   *
   * <p>所有者（createdBy == userId）直接放行；否则遍历有效 ACL 列表，
   * 任一含目标权限位即通过。
   *
   * @param fileNodeId 文件节点 ID
   * @param userId     用户 ID
   * @param roleIds    用户角色 ID 列表（可为空）
   * @param permission 目标权限位
   * @return true 表示拥有权限
   */
  public boolean checkPermission(
      String fileNodeId, String userId, List<String> roleIds, int permission) {
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
   * 查询用户对文件节点的有效 ACL 列表（不含所有者短路判断）。
   *
   * <p>该方法仅做数据库查询，缓存由调用方通过 {@code @Cacheable} 控制。
   *
   * @param fileNodeId 文件节点 ID
   * @param userId     用户 ID
   * @return 有效 ACL 列表（含继承），可能为空
   */
  public List<FileAcl> findEffectiveAcls(String fileNodeId, String userId) {
    return fileAclRepository.findEffectivePermissions(fileNodeId, userId, List.of());
  }
}
