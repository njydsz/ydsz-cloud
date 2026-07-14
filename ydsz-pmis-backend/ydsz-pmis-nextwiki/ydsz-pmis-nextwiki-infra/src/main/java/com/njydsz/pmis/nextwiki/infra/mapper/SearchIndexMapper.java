package com.njydsz.pmis.nextwiki.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.nextwiki.domain.entity.SearchIndex;

/**
 * 搜索索引 MyBatis Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Mapper
public interface SearchIndexMapper extends BaseMapper<SearchIndex> {

    /**
     * 新增或更新索引（PostgreSQL ON CONFLICT 语义）
     */
    int upsert(@Param("index") SearchIndex index);

    /**
     * 根据文件节点ID删除索引（物理删除）
     */
    int deleteByFileNodeId(@Param("fileNodeId") String fileNodeId);

    /**
     * 根据文件节点ID查询索引
     */
    SearchIndex selectByFileNodeId(@Param("fileNodeId") String fileNodeId);

    /**
     * 查询所有未删除的索引记录
     */
    List<SearchIndex> selectAll();

    /**
     * 查询所有未删除的文件节点ID（用于索引重建）
     *
     * @param createdBy 创建人，传 null 查询全部
     */
    @Select({"<script>",
            "SELECT id FROM nw_file_node WHERE deleted = 0 AND node_type = 'file'",
            "<if test='createdBy != null and createdBy != \"\"'>",
            "AND created_by = #{createdBy}",
            "</if>",
            "ORDER BY created_at ASC",
            "</script>"})
    List<String> selectAllFileNodeIds(@Param("createdBy") String createdBy);
}
