package com.njydsz.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.nextwiki.domain.entity.FileAcl;

/**
 * 文件 ACL 仓储接口
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FileAclRepository {

    FileAcl save(FileAcl acl);

    List<FileAcl> findByFileNodeId(String fileNodeId);

    List<FileAcl> findByFileNodeIdAndGrantee(String fileNodeId, String granteeType, String granteeId);

    void deleteByFileNodeId(String fileNodeId);

    /**
     * 查询用户对某文件的权限（含继承）
     */
    List<FileAcl> findEffectivePermissions(String fileNodeId, String userId, List<String> roleIds);

    /**
     * 批量插入继承的 ACL
     */
    void batchSave(List<FileAcl> acls);
}
