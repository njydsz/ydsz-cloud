package com.njydsz.workflow.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.workflow.domain.entity.FlowComment;

/**
 * P2-2: 流程评论 Mapper
 *
 * <p>审批评论多级回复查询。一级评论（parent_comment_id IS NULL）与
 * 回复（parent_comment_id 非空）通过不同索引高效查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface FlowCommentMapper extends BaseMapper<FlowComment> {

    /**
     * 查询实例下全部一级评论（按创建时间正序）。
     *
     * @param tenantId   租户 ID
     * @param instanceId 实例 ID
     * @return 一级评论列表（不含回复）
     */
    @Select("SELECT * FROM ydsz_flow_comment " +
            "WHERE tenant_id = #{tenantId} AND instance_id = #{instanceId} " +
            "AND parent_comment_id IS NULL AND deleted = 0 " +
            "ORDER BY created_at ASC")
    List<FlowComment> listRootComments(@Param("tenantId") String tenantId,
                                          @Param("instanceId") String instanceId);

    /**
     * 查询指定父评论下的全部回复（按创建时间正序，含多级）。
     *
     * <p>一次查询拿到父评论下所有层级的回复，前端递归渲染。
     *
     * @param parentCommentId 父评论 ID
     * @return 回复列表
     */
    @Select("SELECT * FROM ydsz_flow_comment " +
            "WHERE parent_comment_id = #{parentCommentId} AND deleted = 0 " +
            "ORDER BY created_at ASC")
    List<FlowComment> listReplies(@Param("parentCommentId") String parentCommentId);

    /**
     * 查询实例下全部评论（一级 + 回复，按创建时间正序）。
     *
     * <p>前端一次性拉取后本地组装树结构，避免 N+1 查询。
     *
     * @param tenantId   租户 ID
     * @param instanceId 实例 ID
     * @return 全部评论列表
     */
    @Select("SELECT * FROM ydsz_flow_comment " +
            "WHERE tenant_id = #{tenantId} AND instance_id = #{instanceId} " +
            "AND deleted = 0 ORDER BY created_at ASC")
    List<FlowComment> listByInstance(@Param("tenantId") String tenantId,
                                        @Param("instanceId") String instanceId);
}
