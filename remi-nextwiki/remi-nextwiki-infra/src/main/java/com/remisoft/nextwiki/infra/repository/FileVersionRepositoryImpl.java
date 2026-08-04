package com.remisoft.nextwiki.infra.repository;

import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.remisoft.nextwiki.domain.entity.FileVersion;
import com.remisoft.nextwiki.domain.repository.FileVersionRepository;
import com.remisoft.nextwiki.infra.mapper.FileVersionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 文件版本仓储实现
 *
 * @author remi-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class FileVersionRepositoryImpl implements FileVersionRepository {

    private final FileVersionMapper fileVersionMapper;

    /**
     * 插入新版本记录（首次保存或新建版本时调用）。
     *
     * @param version 待持久化的版本实体（含 fileNodeId、版本号、内容引用等）
     * @return 已落库的版本实体（含自增主键）
     */
    @Override
    public FileVersion save(FileVersion version) {
        fileVersionMapper.insert(version);
        return version;
    }

    /**
     * 乐观锁更新版本；若未携带 revision 则退化为普通更新，更新受影响的行数为 0 时抛出
     * {@link OptimisticLockingFailureException}（并发冲突），成功后内存对象 revision 自增 1 以保持一致。
     *
     * @param version 待更新的版本实体（必须携带 id；revision 缺失时走兜底逻辑）
     */
    @Override
    public void update(FileVersion version) {
        if (version.getRevision() == null) {
            // 兜底：未携带 revision 时退化为普通更新，避免业务阻断
            fileVersionMapper.updateById(version);
            return;
        }
        int affected = fileVersionMapper.updateWithRevision(version);
        if (affected == 0) {
            throw new OptimisticLockingFailureException(
                    "FileVersion 乐观锁更新失败，id=" + version.getId()
                            + ", revision=" + version.getRevision());
        }
        version.setRevision(version.getRevision() + 1);
    }

    /**
     * 查询指定文件节点的完整版本历史（按版本号升序）。
     *
     * @param fileNodeId 文件节点 ID
     * @return 版本记录列表（可能为空）
     */
    @Override
    public List<FileVersion> findByFileNodeId(String fileNodeId) {
        return fileVersionMapper.selectByFileNodeId(fileNodeId);
    }

    /**
     * 查询指定文件节点下某一具体版本号的内容（用于回滚、版本对比）。
     *
     * @param fileNodeId 文件节点 ID
     * @param versionNumber 版本号（从 1 开始递增）
     * @return 命中的版本实体；不存在则返回 null
     */
    @Override
    public FileVersion findByFileNodeIdAndVersion(String fileNodeId, Integer versionNumber) {
        return fileVersionMapper.selectByVersion(fileNodeId, versionNumber);
    }

    /**
     * 查询指定文件节点当前激活的版本（is_active = true）。
     *
     * @param fileNodeId 文件节点 ID
     * @return 当前激活版本；不存在则返回 null
     */
    @Override
    public FileVersion findActiveVersion(String fileNodeId) {
        return fileVersionMapper.selectActiveVersion(fileNodeId);
    }

    /**
     * 切换指定文件节点的激活版本；将 versionNumber 对应的版本置为激活，其余置为非激活。
     * 注意：versionNumber 传入 -1 时表示将全部版本置为非激活（用于清空激活态）。
     *
     * @param fileNodeId 文件节点 ID
     * @param versionNumber 目标激活版本号（-1 表示全部非激活）
     */
    @Override
    public void setActiveVersion(String fileNodeId, Integer versionNumber) {
        fileVersionMapper.setActiveVersion(fileNodeId, versionNumber);
    }

    /**
     * 按主键物理删除单条版本记录（不可逆，谨慎调用）。
     *
     * @param id 版本记录主键
     */
    @Override
    public void deleteById(String id) {
        fileVersionMapper.deleteById(id);
    }

    /**
     * 按保留数量清理旧版本：保留最近 keepCount 个版本（version_number DESC），删除其余历史版本，
     * 用于限制版本无限增长、控制存储成本。
     *
     * @param fileNodeId 文件节点 ID
     * @param keepCount 需保留的版本数量
     * @return 被删除的版本行数
     */
    @Override
    public int deleteExcessVersions(String fileNodeId, int keepCount) {
        return fileVersionMapper.deleteExcessVersions(fileNodeId, keepCount);
    }

    /**
     * 统计指定文件节点的版本总数。
     *
     * @param fileNodeId 文件节点 ID
     * @return 版本数量
     */
    @Override
    public int countByFileNodeId(String fileNodeId) {
        return fileVersionMapper.countByFileNodeId(fileNodeId);
    }

    /**
     * 查询指定文件节点最旧的 limit 个版本（按版本号升序），供版本清理/归档策略批量选取待删版本。
     *
     * @param fileNodeId 文件节点 ID
     * @param limit 返回数量上限
     * @return 最旧版本列表
     */
    @Override
    public List<FileVersion> findOldestVersions(String fileNodeId, int limit) {
        return fileVersionMapper.selectOldestVersions(fileNodeId, limit);
    }
}
