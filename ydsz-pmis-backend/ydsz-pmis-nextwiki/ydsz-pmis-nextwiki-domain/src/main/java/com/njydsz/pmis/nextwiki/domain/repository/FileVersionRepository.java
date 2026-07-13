package com.njydsz.pmis.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.pmis.nextwiki.domain.entity.FileVersion;

/**
 * 文件版本仓储接口
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public interface FileVersionRepository {

    /**
     * 保存版本记录
     */
    FileVersion save(FileVersion version);

    /**
     * 查询文件的版本历史
     */
    List<FileVersion> findByFileNodeId(String fileNodeId);

    /**
     * 查询指定版本
     */
    FileVersion findByFileNodeIdAndVersion(String fileNodeId, Integer versionNumber);

    /**
     * 查询当前活跃版本
     */
    FileVersion findActiveVersion(String fileNodeId);

    /**
     * 设置活跃版本
     */
    void setActiveVersion(String fileNodeId, Integer versionNumber);

    /**
     * 删除版本
     */
    void deleteById(String id);

    /**
     * 统计版本数
     */
    int countByFileNodeId(String fileNodeId);

    /**
     * 查询最旧的版本（用于超限清理）
     */
    List<FileVersion> findOldestVersions(String fileNodeId, int limit);
}
