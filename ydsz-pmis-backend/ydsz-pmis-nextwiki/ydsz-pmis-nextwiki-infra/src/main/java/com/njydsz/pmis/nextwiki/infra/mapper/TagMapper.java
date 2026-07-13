package com.njydsz.pmis.nextwiki.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.nextwiki.domain.entity.FileTag;
import com.njydsz.pmis.nextwiki.domain.entity.Tag;

/**
 * 标签 MyBatis Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    Tag selectByName(@Param("name") String name);

    List<Tag> selectAll();

    List<Tag> selectByFileNodeId(@Param("fileNodeId") String fileNodeId);

    @Insert("INSERT INTO nw_file_tag (id, file_node_id, tag_id, created_by, created_at, updated_by, updated_at, revision, deleted) " +
            "VALUES (#{id}, #{fileNodeId}, #{tagId}, #{createdBy}, NOW(), #{updatedBy}, NOW(), 0, 0)")
    int insertFileTag(FileTag fileTag);

    @Delete("DELETE FROM nw_file_tag WHERE file_node_id = #{fileNodeId} AND tag_id = #{tagId}")
    int deleteFileTag(@Param("fileNodeId") String fileNodeId, @Param("tagId") String tagId);

    @Delete("DELETE FROM nw_file_tag WHERE file_node_id = #{fileNodeId}")
    int deleteAllFileTags(@Param("fileNodeId") String fileNodeId);

    List<FileTag> selectFileTagsByFileNodeId(@Param("fileNodeId") String fileNodeId);

    @Update("UPDATE nw_tag SET usage_count = usage_count + 1 WHERE id = #{tagId}")
    int incrementUsage(@Param("tagId") String tagId);

    @Update("UPDATE nw_tag SET usage_count = GREATEST(usage_count - 1, 0) WHERE id = #{tagId}")
    int decrementUsage(@Param("tagId") String tagId);
}
