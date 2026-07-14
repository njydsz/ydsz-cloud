package com.njydsz.pmis.nextwiki.infra.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.njydsz.pmis.nextwiki.domain.entity.FileVersion;
import com.njydsz.pmis.nextwiki.domain.repository.FileVersionRepository;
import com.njydsz.pmis.nextwiki.infra.mapper.FileVersionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 文件版本仓储实现
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Repository
@RequiredArgsConstructor
public class FileVersionRepositoryImpl implements FileVersionRepository {

    private final FileVersionMapper fileVersionMapper;

    @Override
    public FileVersion save(FileVersion version) {
        fileVersionMapper.insert(version);
        return version;
    }

    @Override
    public List<FileVersion> findByFileNodeId(String fileNodeId) {
        return fileVersionMapper.selectByFileNodeId(fileNodeId);
    }

    @Override
    public FileVersion findByFileNodeIdAndVersion(String fileNodeId, Integer versionNumber) {
        return fileVersionMapper.selectByVersion(fileNodeId, versionNumber);
    }

    @Override
    public FileVersion findActiveVersion(String fileNodeId) {
        return fileVersionMapper.selectActiveVersion(fileNodeId);
    }

    @Override
    public void setActiveVersion(String fileNodeId, Integer versionNumber) {
        fileVersionMapper.setActiveVersion(fileNodeId, versionNumber);
    }

    @Override
    public void deleteById(String id) {
        fileVersionMapper.deleteById(id);
    }

    @Override
    public int deleteExcessVersions(String fileNodeId, int keepCount) {
        return fileVersionMapper.deleteExcessVersions(fileNodeId, keepCount);
    }

    @Override
    public int countByFileNodeId(String fileNodeId) {
        return fileVersionMapper.countByFileNodeId(fileNodeId);
    }

    @Override
    public List<FileVersion> findOldestVersions(String fileNodeId, int limit) {
        return fileVersionMapper.selectOldestVersions(fileNodeId, limit);
    }
}
