package com.njydsz.workflow.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.workflow.domain.entity.FlowComment;

/**
 * P2-2 流程评论 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_comment</code>，存储审批评论与多级回复。
 *
 * <p>一级评论（{@code parent_comment_id IS NULL}）与回复通过不同索引高效查询；评论支持 @ 提醒（{@code mentioned_user_ids}）与表情。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>idx_instance_id — 流程实例维度查询索引
 *   <li>idx_parent_id — 父子层级索引（多级回复）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.domain.entity.FlowComment 评论实体
 * @see com.njydsz.workflow.server.service.FlowCommentService 评论 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowCommentMapper extends BaseMapper<FlowComment> {

  /**
   * 查询实例下全部一级评论（按创建时间正序）。
   *
   * @param tenantId 租户 ID
   * @param instanceId 实例 ID
   * @return 一级评论列表（不含回复）
   */
  @Select(
      "SELECT * FROM ydsz_flow_comment "
          + "WHERE tenant_id = #{tenantId} AND instance_id = #{instanceId} "
          + "AND parent_comment_id IS NULL AND deleted = 0 "
          + "ORDER BY created_at ASC")
  List<FlowComment> listRootComments(
      @Param("tenantId") String tenantId, @Param("instanceId") String instanceId);

  /**
   * 查询指定父评论下的全部回复（按创建时间正序，含多级）。
   *
   * <p>一次查询拿到父评论下所有层级的回复，前端递归渲染。
   *
   * @param parentCommentId 父评论 ID
   * @return 回复列表
   */
  @Select(
      "SELECT * FROM ydsz_flow_comment "
          + "WHERE parent_comment_id = #{parentCommentId} AND deleted = 0 "
          + "ORDER BY created_at ASC")
  List<FlowComment> listReplies(@Param("parentCommentId") String parentCommentId);

  /**
   * 查询实例下全部评论（一级 + 回复，按创建时间正序）。
   *
   * <p>前端一次性拉取后本地组装树结构，避免 N+1 查询。
   *
   * @param tenantId 租户 ID
   * @param instanceId 实例 ID
   * @return 全部评论列表
   */
  @Select(
      "SELECT * FROM ydsz_flow_comment "
          + "WHERE tenant_id = #{tenantId} AND instance_id = #{instanceId} "
          + "AND deleted = 0 ORDER BY created_at ASC")
  List<FlowComment> listByInstance(
      @Param("tenantId") String tenantId, @Param("instanceId") String instanceId);
}
