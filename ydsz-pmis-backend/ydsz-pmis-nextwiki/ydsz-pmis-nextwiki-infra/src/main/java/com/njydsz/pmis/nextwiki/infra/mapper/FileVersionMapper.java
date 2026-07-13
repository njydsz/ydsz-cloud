package com.njydsz.pmis.nextwiki.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.nextwiki.domain.entity.FileVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文件版本 MyBatis Mapper
 *
 * @author ydsz-pmis-team
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
}
