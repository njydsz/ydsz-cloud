package com.njydsz.pmis.nextwiki.infra.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.njydsz.pmis.nextwiki.domain.entity.FileAcl;
import com.njydsz.pmis.nextwiki.domain.repository.FileAclRepository;
import com.njydsz.pmis.nextwiki.infra.mapper.FileAclMapper;

import lombok.RequiredArgsConstructor;

/**
 * 文件 ACL 仓储实现
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Repository
@RequiredArgsConstructor
public class FileAclRepositoryImpl implements FileAclRepository {

    private final FileAclMapper fileAclMapper;

    @Override
    public FileAcl save(FileAcl acl) {
        fileAclMapper.insert(acl);
        return acl;
    }

    @Override
    public List<FileAcl> findByFileNodeId(String fileNodeId) {
        return fileAclMapper.selectByFileNodeId(fileNodeId);
    }

    @Override
    public List<FileAcl> findByFileNodeIdAndGrantee(String fileNodeId, String granteeType, String granteeId) {
        return fileAclMapper.selectByFileNodeIdAndGrantee(fileNodeId, granteeType, granteeId);
    }

    @Override
    public void deleteByFileNodeId(String fileNodeId) {
        fileAclMapper.deleteByFileNodeId(fileNodeId);
    }

    @Override
    public List<FileAcl> findEffectivePermissions(String fileNodeId, String userId, List<String> roleIds) {
        return fileAclMapper.selectEffectivePermissions(fileNodeId, userId, roleIds);
    }

    @Override
    public void batchSave(List<FileAcl> acls) {
        if (acls != null && !acls.isEmpty()) {
            fileAclMapper.batchInsert(acls);
        }
    }
}
