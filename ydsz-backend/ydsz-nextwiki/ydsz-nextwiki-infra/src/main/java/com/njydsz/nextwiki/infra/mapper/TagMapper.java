package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.nextwiki.domain.entity.FileTag;
import com.njydsz.nextwiki.domain.entity.Tag;

/**
 * 标签 Mapper
 *
 * <p>对应数据表 <code>ydsz_tag</code>。
 * <p>标签是文件分类/检索的辅助手段，与文件是多对多关系（{@code ydsz_file_tag}）。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_tag_name — (租户+标签名) 唯一索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.nextwiki.domain.entity.Tag 标签实体
 * @see com.njydsz.nextwiki.server.service.TagService 标签 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
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

    @Select("SELECT ft.file_node_id FROM nw_file_tag ft " +
            "INNER JOIN nw_tag t ON ft.tag_id = t.id " +
            "WHERE t.name LIKE CONCAT('%', #{tagName}, '%') AND ft.deleted = 0 AND t.deleted = 0")
    List<String> findFileNodeIdsByTagName(@Param("tagName") String tagName);
}
