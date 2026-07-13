package com.njydsz.pmis.nextwiki.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.nextwiki.domain.entity.FileNode;

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
}
