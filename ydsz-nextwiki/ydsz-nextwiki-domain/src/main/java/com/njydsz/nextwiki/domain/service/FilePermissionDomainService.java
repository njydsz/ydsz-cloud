package com.njydsz.nextwiki.domain.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.FileAclDTO;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;

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
 * <p><b>设计原则：</b>本服务仅负责纯领域逻辑，不依赖任何仓储接口。
 * 数据访问由 server 层负责，通过方法参数传入所需数据；缓存控制由应用服务层通过
 * {@code @Cacheable} / {@code @CacheEvict} 管理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class FilePermissionDomainService {

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /** 权限位常量 */
  public static final int PERM_READ = 1;
  public static final int PERM_WRITE = 2;
  public static final int PERM_DELETE = 4;
  public static final int PERM_SHARE = 8;
  public static final int PERM_DOWNLOAD = 16;
  public static final int PERM_ALL = PERM_READ | PERM_WRITE | PERM_DELETE | PERM_SHARE | PERM_DOWNLOAD;

  /**
   * 构建 ACL DTO（领域工厂方法）。
   *
   * <p>由 server 层传入所需字段，组装并返回待持久化的 ACL DTO。
   * 持久化操作（{@code repository.save}）由 server 层负责。
   *
   * @param fileNodeId    文件节点 ID
   * @param granteeType   授权对象类型（user/role/group/tenant）
   * @param granteeId     授权对象 ID
   * @param permissionMask 权限掩码（位运算组合）
   * @param userId        操作人 ID
   * @return 构建完成的 ACL DTO（未持久化）
   */
  public FileAclDTO buildAcl(
      String fileNodeId, String granteeType, String granteeId, int permissionMask, String userId) {
    FileAclDTO acl =
        FileAclDTO.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()))
            .fileNodeId(fileNodeId)
            .granteeType(granteeType)
            .granteeId(granteeId)
            .permissionMask(permissionMask)
            .inherited(false)
            .owner(false)
            .build();

    acl.setCreatedBy(userId);
    acl.setUpdatedBy(userId);

    log.info(
        "[FilePermissionDomainService] 构建 ACL: fileNodeId={}, granteeType={}, granteeId={}, mask={}",
        fileNodeId,
        granteeType,
        granteeId,
        permissionMask);
    return acl;
  }

  /**
   * 设置文件所有者（授予全部权限）。
   *
   * @param fileNodeId 文件节点 ID
   * @param userId     用户 ID
   * @return 构建完成的 ACL 实体（未持久化）
   */
  public FileAclDTO buildOwnerAcl(String fileNodeId, String userId) {
    return buildAcl(fileNodeId, "user", userId, PERM_ALL, userId);
  }

  /**
   * 检查用户是否拥有指定权限（纯领域逻辑，不含数据访问）。
   *
   * <p>所有者（createdBy == userId）直接放行；否则遍历有效 ACL 列表，
   * 任一含目标权限位即通过。
   *
   * @param node     文件节点 VO（由 server 层查询传入，可为 null）
   * @param userId   用户 ID
   * @param acls     有效 ACL 列表（由 server 层查询传入，含继承）
   * @param permission 目标权限位
   * @return true 表示拥有权限
   */
  public boolean checkPermission(FileNodeVO node, String userId, List<FileAclDTO> acls, int permission) {
    // 所有者短路判断
    if (node != null && userId.equals(node.getCreatedBy())) {
      return true;
    }

    // ACL 位运算校验
    if (acls != null) {
      for (FileAclDTO acl : acls) {
        if (hasPermission(acl, permission)) {
          return true;
        }
      }
    }
    return false;
  }

  /** 检查 ACL 是否拥有指定权限 */
  public static boolean hasPermission(FileAclDTO acl, int permission) {
    return acl.getPermissionMask() != null && (acl.getPermissionMask() & permission) == permission;
  }
}
