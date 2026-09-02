package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.njydsz.nextwiki.infra.entity.FileComment;

/**
 * 文件评论 Mapper
 *
 * <p>对应数据表 <code>nw_file_comment</code>。
 *
 * <p>文件评论/回复/批注的持久化访问，支持按文件节点查询、按父评论查询回复、
 * 更新内容、删除（逻辑删除）、标记已解决。
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件（对 MP 自动生成的 SQL 生效）；
 * 手写 SQL 需显式携带租户条件。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.nextwiki.infra.entity.FileComment 文件评论实体
 */
@Mapper
public interface FileCommentMapper extends BaseMapper<FileComment> {

  /**
   * 插入评论。
   *
   * @param entity 评论实体
   * @return 受影响行数
   */
  @Insert(
      "INSERT INTO nw_file_comment (id, file_node_id, content, parent_comment_id, resolved, position, edited, "
          + "created_by, created_at, updated_by, updated_at, revision, deleted, tenant_id) "
          + "VALUES (#{id}, #{fileNodeId}, #{content}, #{parentCommentId}, #{resolved}, #{position}, #{edited}, "
          + "#{createdBy}, NOW(), #{updatedBy}, NOW(), 0, 0, #{tenantId})")
  int insertFileComment(FileComment entity);

  /**
   * 按 ID 查询评论（未删除）。
   *
   * @param id 评论 ID
   * @return 评论实体（不存在时为 null）
   */
  @Select(
      "SELECT * FROM nw_file_comment WHERE id = #{id} AND deleted = 0")
  FileComment selectFileCommentById(@Param("id") String id);

  /**
   * 查询某文件节点的全部顶级评论（按时间正序）。
   *
   * @param fileNodeId 文件节点 ID
   * @return 顶级评论列表
   */
  @Select(
      "SELECT * FROM nw_file_comment WHERE file_node_id = #{fileNodeId} AND deleted = 0 "
          + "AND parent_comment_id IS NULL ORDER BY created_at ASC")
  List<FileComment> selectFileCommentsByFileNodeId(@Param("fileNodeId") String fileNodeId);

  /**
   * 查询某评论的回复列表（按时间正序）。
   *
   * @param parentCommentId 父评论 ID
   * @return 回复列表
   */
  @Select(
      "SELECT * FROM nw_file_comment WHERE parent_comment_id = #{parentCommentId} AND deleted = 0 "
          + "ORDER BY created_at ASC")
  List<FileComment> selectFileCommentReplies(@Param("parentCommentId") String parentCommentId);

  /**
   * 更新评论内容与编辑标记。
   *
   * @param entity 评论实体（携带更新内容与 revision）
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_file_comment SET content = #{content}, edited = TRUE, updated_by = #{updatedBy}, "
          + "updated_at = NOW(), revision = revision + 1 WHERE id = #{id} AND deleted = 0")
  int updateFileComment(FileComment entity);

  /**
   * 逻辑删除评论（及其回复由调用方级联处理）。
   *
   * @param id 评论 ID
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_file_comment SET deleted = 1, updated_at = NOW() WHERE id = #{id} AND deleted = 0")
  int deleteFileComment(@Param("id") String id);

  /**
   * 标记评论已解决（批注闭环）。
   *
   * @param id 评论 ID
   * @param userId 操作人用户 ID
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_file_comment SET resolved = TRUE, updated_by = #{userId}, updated_at = NOW(), "
          + "revision = revision + 1 WHERE id = #{id} AND deleted = 0")
  int markFileCommentResolved(@Param("id") String id, @Param("userId") String userId);
}
