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

    /**
     * 按标签名精确查询（命中 uk_tag_name 唯一索引）；租户隔离由拦截器自动注入。
     *
     * @param name 标签名称
     * @return 命中的标签实体；不存在则返回 null
     */
    Tag selectByName(@Param("name") String name);

    /**
     * 查询当前租户下的全部标签。
     *
     * @return 标签列表
     */
    List<Tag> selectAll();

    /**
     * 查询指定文件节点已绑定的全部标签。
     *
     * @param fileNodeId 文件节点 ID
     * @return 标签列表
     */
    List<Tag> selectByFileNodeId(@Param("fileNodeId") String fileNodeId);

    /**
     * 插入文件-标签关联记录（中间表 nw_file_tag）。
     *
     * @param fileTag 关联实体（id/fileNodeId/tagId/createdBy/updatedBy 由调用方填充，revision/deleted 默认 0）
     * @return 受影响行数
     */
    @Insert("INSERT INTO nw_file_tag (id, file_node_id, tag_id, created_by, created_at, updated_by, updated_at, revision, deleted) " +
            "VALUES (#{id}, #{fileNodeId}, #{tagId}, #{createdBy}, NOW(), #{updatedBy}, NOW(), 0, 0)")
    int insertFileTag(FileTag fileTag);

    /**
     * 删除指定文件节点与指定标签的单条关联。
     *
     * @param fileNodeId 文件节点 ID
     * @param tagId 标签 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM nw_file_tag WHERE file_node_id = #{fileNodeId} AND tag_id = #{tagId}")
    int deleteFileTag(@Param("fileNodeId") String fileNodeId, @Param("tagId") String tagId);

    /**
     * 删除指定文件节点的全部标签关联（文件删除/移出回收站时级联清理）。
     *
     * @param fileNodeId 文件节点 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM nw_file_tag WHERE file_node_id = #{fileNodeId}")
    int deleteAllFileTags(@Param("fileNodeId") String fileNodeId);

    /**
     * 查询指定文件节点的全部文件-标签关联记录。
     *
     * @param fileNodeId 文件节点 ID
     * @return 关联记录列表
     */
    List<FileTag> selectFileTagsByFileNodeId(@Param("fileNodeId") String fileNodeId);

    /**
     * 标签使用计数 +1（绑定标签时调用），原子自增避免并发计数偏差。
     *
     * @param tagId 标签 ID
     * @return 受影响行数
     */
    @Update("UPDATE nw_tag SET usage_count = usage_count + 1 WHERE id = #{tagId}")
    int incrementUsage(@Param("tagId") String tagId);

    /**
     * 标签使用计数 -1（解绑标签时调用）；使用 GREATEST(usage_count - 1, 0) 防止计数出现负数。
     *
     * @param tagId 标签 ID
     * @return 受影响行数
     */
    @Update("UPDATE nw_tag SET usage_count = GREATEST(usage_count - 1, 0) WHERE id = #{tagId}")
    int decrementUsage(@Param("tagId") String tagId);

    /**
     * 按标签名模糊匹配，返回关联的文件节点 ID 列表，用于按标签检索/聚合文件。
     *
     * @param tagName 标签名模糊关键字（LIKE %tagName%）
     * @return 命中标签所关联的文件节点 ID 列表
     */
    @Select("SELECT ft.file_node_id FROM nw_file_tag ft " +
            "INNER JOIN nw_tag t ON ft.tag_id = t.id " +
            "WHERE t.name LIKE CONCAT('%', #{tagName}, '%') AND ft.deleted = 0 AND t.deleted = 0")
    List<String> findFileNodeIdsByTagName(@Param("tagName") String tagName);
}
