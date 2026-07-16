package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.nextwiki.domain.entity.FileVersion;

/**
 * 文件版本 MyBatis Mapper
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Mapper
public interface FileVersionMapper extends BaseMapper<FileVersion> {

    /**
     * 查询文件的版本历史
     */
    List<FileVersion> selectByFileNodeId(@Param("fileNodeId") String fileNodeId);

    /**
     * 查询指定版本
     */
    FileVersion selectByVersion(@Param("fileNodeId") String fileNodeId, @Param("versionNumber") Integer versionNumber);

    /**
     * 查询活跃版本
     */
    FileVersion selectActiveVersion(@Param("fileNodeId") String fileNodeId);

    /**
     * 设置活跃版本（-1 表示全部设为非活跃）
     */
    @Update("UPDATE nw_file_version SET is_active = CASE WHEN version_number = #{versionNumber} THEN true ELSE false END " +
            "WHERE file_node_id = #{fileNodeId}")
    int setActiveVersion(@Param("fileNodeId") String fileNodeId, @Param("versionNumber") Integer versionNumber);

    /**
     * 统计版本数
     */
    int countByFileNodeId(@Param("fileNodeId") String fileNodeId);

    /**
     * 查询最旧版本（按版本号升序）
     */
    List<FileVersion> selectOldestVersions(@Param("fileNodeId") String fileNodeId, @Param("limit") int limit);

    /**
     * 批量删除指定文件节点中除保留版本外的所有旧版本
     * <p>
     * 保留最近 {@code keepCount} 个版本（按 version_number DESC），删除其余。
     *
     * @param fileNodeId 文件节点ID
     * @param keepCount  保留的版本数量
     * @return 受影响行数
     */
    @Delete("DELETE FROM nw_file_version WHERE file_node_id = #{fileNodeId} AND deleted = 0 " +
            "AND id NOT IN (" +
            "  SELECT id FROM (" +
            "    SELECT id FROM nw_file_version " +
            "    WHERE file_node_id = #{fileNodeId} AND deleted = 0 " +
            "    ORDER BY version_number DESC LIMIT #{keepCount}" +
            "  ) AS keep_ids" +
            ")")
    int deleteExcessVersions(@Param("fileNodeId") String fileNodeId, @Param("keepCount") int keepCount);

    /**
     * 带 revision 乐观锁的更新（更新失败返回 0）
     */
    int updateWithRevision(@Param("version") FileVersion version);
}
