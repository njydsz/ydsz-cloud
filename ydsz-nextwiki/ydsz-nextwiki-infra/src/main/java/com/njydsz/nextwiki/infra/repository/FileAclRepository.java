package com.njydsz.nextwiki.infra.repository;

import java.util.List;

import com.njydsz.nextwiki.domain.entity.FileAcl;

/**
 * 文件 ACL 仓储接口
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FileAclRepository {

  /**
   * 保存单条 ACL 记录（新增或更新）。
   *
   * @param acl 待持久化的 ACL 实体（含授权对象、权限位掩码等）
   * @return 持久化后的 ACL（回填主键）
   */
  FileAcl save(FileAcl acl);

  /**
   * 查询某文件节点上配置的全部 ACL 记录（含继承与所有者）。
   *
   * @param fileNodeId 文件节点 ID
   * @return 该节点的 ACL 列表，无记录时返回空列表
   */
  List<FileAcl> findByFileNodeId(String fileNodeId);

  /**
   * 精确查询某文件节点上授予给指定授权对象的 ACL 记录。
   *
   * @param fileNodeId 文件节点 ID
   * @param granteeType 授权对象类型（user/role/group/tenant）
   * @param granteeId 授权对象 ID
   * @return 匹配的 ACL 列表，可能为空
   */
  List<FileAcl> findByFileNodeIdAndGrantee(String fileNodeId, String granteeType, String granteeId);

  /**
   * 删除某文件节点上的全部 ACL 记录（删除文件/目录时级联清理权限）。
   *
   * @param fileNodeId 文件节点 ID
   */
  void deleteByFileNodeId(String fileNodeId);

  /** 查询用户对某文件的权限（含继承） */
  List<FileAcl> findEffectivePermissions(String fileNodeId, String userId, List<String> roleIds);

  /** 批量插入继承的 ACL */
  void batchSave(List<FileAcl> acls);
}
