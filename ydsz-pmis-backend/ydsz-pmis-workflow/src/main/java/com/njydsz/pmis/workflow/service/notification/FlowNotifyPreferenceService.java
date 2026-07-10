package com.njydsz.pmis.workflow.service.notification;

import com.njydsz.pmis.workflow.entity.notification.FlowNotifyPreferenceDO;

/**
 * P1-7: 工作流通知偏好 Service
 *
 * <p>管理用户免打扰时段（quietHours）与通知聚合（digestMode）偏好。
 * 在通知投递前调用 {@link #shouldDefer(String, String)} 判断是否应延迟投递。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
public interface FlowNotifyPreferenceService {

    /**
     * 查询当前用户的通知偏好（不存在时返回默认偏好：无免打扰、立即投递）。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 偏好 DO（不存在时返回 new 的默认实例，不写库）
     */
    FlowNotifyPreferenceDO getOrCreate(String tenantId, String userId);

    /**
     * 保存（新增或更新）当前用户的通知偏好。每个用户在租户内至多一条记录。
     *
     * @param tenantId   租户 ID
     * @param userId     用户 ID
     * @param preference 偏好数据（quietHoursStart / quietHoursEnd / digestMode）
     * @return 偏好记录 ID
     */
    String save(String tenantId, String userId, FlowNotifyPreferenceDO preference);

    /**
     * 判断当前时刻是否处于用户的免打扰时段。
     *
     * <p>判断条件：偏好存在、quietHoursStart/End 非空且格式合法、当前时间落在配置区间内。
     * 跨午夜场景（如 22:00→08:00）按"start &gt;= end 时跨午夜"处理。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return true 表示当前在免打扰时段内
     */
    boolean isQuietHours(String tenantId, String userId);

    /**
     * 判断该用户的通知是否应延迟投递。
     *
     * <p>仅当 digestMode=1（启用聚合）且当前处于免打扰时段时返回 true。
     * digestMode=0（立即投递）始终返回 false，即使处于免打扰时段也立即发送。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return true 表示应延迟投递
     */
    boolean shouldDefer(String tenantId, String userId);
}
