package com.njydsz.pmis.workflow.mapper.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.notification.FlowNotifyPreferenceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * P1-7: 工作流通知偏好 Mapper
 *
 * <p>用户免打扰时段与通知聚合偏好查询。每个用户在租户内至多一条偏好记录。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Mapper
public interface FlowNotifyPreferenceMapper extends BaseMapper<FlowNotifyPreferenceDO> {

    /**
     * 按租户 + 用户 ID 查询偏好记录。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 偏好 DO，不存在返回 null
     */
    @Select("SELECT * FROM pmis_flow_notify_preference " +
            "WHERE tenant_id = #{tenantId} AND user_id = #{userId} " +
            "AND deleted = 0 LIMIT 1")
    FlowNotifyPreferenceDO selectByUserId(@Param("tenantId") String tenantId,
                                           @Param("userId") String userId);
}
