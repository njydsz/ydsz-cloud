package com.njydsz.nextwiki.infra.repository;

import com.njydsz.nextwiki.domain.entity.FileAcl;
import com.njydsz.nextwiki.domain.repository.FileAclRepository;
import com.njydsz.nextwiki.infra.mapper.FileAclMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 文件 ACL 仓储实现
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class FileAclRepositoryImpl implements FileAclRepository {

  private final FileAclMapper fileAclMapper;

  /**
   * 持久化单条文件权限 ACL 记录。
   *
   * @param acl 待保存的 ACL 实体（含 fileNodeId、主体类型/ID、权限三元组）
   * @return 已落库的 ACL 实体（含自增主键等数据库回填字段）
   */
  @Override
  public FileAcl save(FileAcl acl) {
    fileAclMapper.insert(acl);
    return acl;
  }

  /**
   * 查询指定文件节点下的全部 ACL 规则。
   *
   * @param fileNodeId 文件节点 ID
   * @return 该节点下的 ACL 规则列表（可能为空；已逻辑删除记录由拦截器自动过滤）
   */
  @Override
  public List<FileAcl> findByFileNodeId(String fileNodeId) {
    return fileAclMapper.selectByFileNodeId(fileNodeId);
  }

  /**
   * 精确查询某文件节点下、某授权主体（用户/角色/组）的 ACL 规则，供鉴权判定使用。
   *
   * @param fileNodeId 文件节点 ID
   * @param granteeType 主体类型：user / role / group
   * @param granteeId 主体 ID
   * @return 命中的 ACL 规则列表
   */
  @Override
  public List<FileAcl> findByFileNodeIdAndGrantee(
      String fileNodeId, String granteeType, String granteeId) {
    return fileAclMapper.selectByFileNodeIdAndGrantee(fileNodeId, granteeType, granteeId);
  }

  /**
   * 删除指定文件节点下的所有 ACL 规则（随节点删除级联清理，避免残留越权规则）。
   *
   * @param fileNodeId 文件节点 ID
   */
  @Override
  public void deleteByFileNodeId(String fileNodeId) {
    fileAclMapper.deleteByFileNodeId(fileNodeId);
  }

  /**
   * 查询某用户在某文件节点上的有效权限，含从父目录继承的权限，是权限校验的核心入口。
   *
   * @param fileNodeId 文件节点 ID
   * @param userId 当前用户 ID（用于匹配 user 类 ACL 及后续角色展开）
   * @param roleIds 用户所属角色 ID 列表（用于匹配 role 类 ACL）
   * @return 命中有效权限的 ACL 列表
   */
  @Override
  public List<FileAcl> findEffectivePermissions(
      String fileNodeId, String userId, List<String> roleIds) {
    return fileAclMapper.selectEffectivePermissions(fileNodeId, userId, roleIds);
  }

  /**
   * 批量保存 ACL 规则；入参为 null 或空集合时直接跳过，避免执行无意义的批量插入 SQL。
   *
   * @param acls 待保存的 ACL 列表
   */
  @Override
  public void batchSave(List<FileAcl> acls) {
    if (acls != null && !acls.isEmpty()) {
      fileAclMapper.batchInsert(acls);
    }
  }
}
