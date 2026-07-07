package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowAiFeedbackDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * AI 推荐审批人反馈 Mapper
 *
 * <p>P3-3: 对应 pmis_flow_ai_feedback 表，存储用户对 AI 推荐审批人的反馈记录。
 * 通用 CRUD 由 MyBatis-Plus {@link BaseMapper} 提供，统计查询用注解 SQL。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowAiFeedbackMapper extends BaseMapper<FlowAiFeedbackDO> {

    /**
     * P3-3: 按租户 + 推荐人维度统计反馈分布。
     *
     * <p>用于推荐准确率分析：统计某推荐人被接受/拒绝/选择其他人的次数。
     *
     * @param tenantId 租户 ID
     * @param recommendedUserId 推荐人 ID（可空，空则统计全部）
     * @return 每行包含 action + cnt 字段
     */
    @Select("""
            <script>
            SELECT action, COUNT(*) AS cnt
            FROM pmis_flow_ai_feedback
            WHERE deleted = 0 AND tenant_id = #{tenantId}
            <if test="recommendedUserId != null and recommendedUserId != ''">
              AND recommended_user_id = #{recommendedUserId}
            </if>
            GROUP BY action
            </script>
            """)
    List<Map<String, Object>> selectFeedbackStats(@Param("tenantId") String tenantId,
                                                   @Param("recommendedUserId") String recommendedUserId);

    /**
     * P3-3: 查询某推荐人最近 N 次被反馈的记录（用于推荐时作为历史上下文）。
     *
     * @param tenantId 租户 ID
     * @param recommendedUserId 推荐人 ID
     * @param limit 返回条数
     * @return 反馈记录列表（按 created_at 倒序）
     */
    @Select("""
            SELECT *
            FROM pmis_flow_ai_feedback
            WHERE deleted = 0 AND tenant_id = #{tenantId}
              AND recommended_user_id = #{recommendedUserId}
            ORDER BY created_at DESC
            LIMIT #{limit}
            """)
    List<FlowAiFeedbackDO> selectRecentFeedback(@Param("tenantId") String tenantId,
                                                 @Param("recommendedUserId") String recommendedUserId,
                                                 @Param("limit") int limit);
}
