package com.njydsz.pmis.nextwiki.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.repository.FileNodeRepository;

/**
 * 文件节点 MyBatis Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Mapper
public interface FileNodeMapper extends BaseMapper<FileNode> {

    /**
     * 查询子节点（未删除）
     */
    List<FileNode> selectChildren(@Param("parentId") String parentId);

    /**
     * 按路径前缀查询（用于递归操作）
     */
    List<FileNode> selectByPathPrefix(@Param("pathPrefix") String pathPrefix);

    /**
     * 逻辑删除（设置 deleted=1 + deleted_time）
     */
    @Update("UPDATE nw_file_node SET deleted = 1, deleted_time = NOW(), " +
            "original_path = #{originalPath}, updated_at = NOW() WHERE id = #{id}")
    int softDelete(@Param("id") String id, @Param("originalPath") String originalPath);

    /**
     * 恢复逻辑删除
     */
    @Update("UPDATE nw_file_node SET deleted = 0, deleted_time = NULL, updated_at = NOW() WHERE id = #{id}")
    int restore(@Param("id") String id);

    /**
     * 更新大小
     */
    @Update("UPDATE nw_file_node SET size = size + #{sizeDelta}, updated_at = NOW() WHERE id = #{id}")
    int updateSize(@Param("id") String id, @Param("sizeDelta") Long sizeDelta);

    /**
     * 搜索文件名（LIKE）
     */
    List<FileNode> searchByName(@Param("keyword") String keyword, @Param("createdBy") String createdBy);

    /**
     * 查询用户根目录
     */
    FileNode selectRootByUser(@Param("createdBy") String createdBy);

    /**
     * 统计用户文件数量
     */
    @Select("SELECT COUNT(*) FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'file'")
    int countByUser(@Param("userId") String userId);

    /**
     * 查询用户文件总大小
     */
    @Select("SELECT COALESCE(SUM(size), 0) FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'file'")
    Long sumSizeByUser(@Param("userId") String userId);

    /**
     * 查询用户大文件 Top-N
     */
    @Select("SELECT * FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'file' " +
            "ORDER BY size DESC LIMIT #{limit}")
    List<FileNode> findTopLargeFilesByUser(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 按后缀统计文件数量和大小
     */
    @Select("SELECT suffix, COUNT(*) AS file_count, COALESCE(SUM(size), 0) AS total_size " +
            "FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'file' " +
            "GROUP BY suffix ORDER BY total_size DESC")
    List<FileNodeRepository.FileTypeStat> statsBySuffixAndUser(@Param("userId") String userId);

    /**
     * 按文件哈希查询（用于秒传去重）
     */
    @Select("SELECT * FROM nw_file_node WHERE file_hash = #{fileHash} AND deleted = 0 AND node_type = 'file' LIMIT 1")
    FileNode findByFileHash(@Param("fileHash") String fileHash);

    /**
     * 按 createdBy + parentId 查询同名文件
     */
    @Select("SELECT * FROM nw_file_node WHERE name = #{name} AND parent_id = #{parentId} " +
            "AND created_by = #{createdBy} AND deleted = 0")
    List<FileNode> findByNameAndParent(@Param("name") String name, @Param("parentId") String parentId, @Param("createdBy") String createdBy);
}
