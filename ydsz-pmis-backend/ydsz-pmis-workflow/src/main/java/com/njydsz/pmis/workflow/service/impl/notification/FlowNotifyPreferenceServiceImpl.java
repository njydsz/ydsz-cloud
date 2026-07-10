package com.njydsz.pmis.workflow.service.impl.notification;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.entity.notification.FlowNotifyPreferenceDO;
import com.njydsz.pmis.workflow.mapper.notification.FlowNotifyPreferenceMapper;
import com.njydsz.pmis.workflow.service.notification.FlowNotifyPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * P1-7: 工作流通知偏好 Service 实现
 *
 * <p>免打扰时段判断支持跨午夜场景（如 22:00→08:00），格式非法时 fail-open（不静默）。
 * 通知聚合（digestMode=1）+ 免打扰时段内 → {@link #shouldDefer} 返回 true，
 * 调用方据此跳过立即推送，待免打扰时段结束后由用户主动查看待办列表。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowNotifyPreferenceServiceImpl implements FlowNotifyPreferenceService {

    /** digestMode=1 表示启用聚合（免打扰时段内延迟投递） */
    private static final int DIGEST_MODE_AGGREGATE = 1;

    /** 通知偏好 Mapper，负责 pmis_flow_notify_preference 表的增删改查及按租户+用户查询偏好配置 */
    private final FlowNotifyPreferenceMapper preferenceMapper;

    @Override
    @Transactional(readOnly = true)
    public FlowNotifyPreferenceDO getOrCreate(String tenantId, String userId) {
        String effectiveTenant = tenantId == null ? "1" : tenantId;
        FlowNotifyPreferenceDO pref = preferenceMapper.selectByUserId(effectiveTenant, userId);
        if (pref != null) {
            return pref;
        }
        // 返回默认实例（不写库），digestMode=0 表示立即投递
        FlowNotifyPreferenceDO def = new FlowNotifyPreferenceDO();
        def.setTenantId(effectiveTenant);
        def.setUserId(userId);
        def.setDigestMode(0);
        return def;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(String tenantId, String userId, FlowNotifyPreferenceDO preference) {
        if (!StringUtils.hasText(userId)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_a7b8c9d0");
        }
        validateTimeFormat(preference.getQuietHoursStart(), "quietHoursStart");
        validateTimeFormat(preference.getQuietHoursEnd(), "quietHoursEnd");
        String effectiveTenant = tenantId == null ? "1" : tenantId;

        FlowNotifyPreferenceDO existing = preferenceMapper.selectByUserId(effectiveTenant, userId);
        if (existing == null) {
            preference.setTenantId(effectiveTenant);
            preference.setUserId(userId);
            if (preference.getDigestMode() == null) {
                preference.setDigestMode(0);
            }
            preferenceMapper.insert(preference);
            log.info("[FlowNotifyPreference] 新增偏好: tenant={} userId={} quiet={}~{} digest={}",
                    effectiveTenant, userId,
                    preference.getQuietHoursStart(), preference.getQuietHoursEnd(),
                    preference.getDigestMode());
            return preference.getId();
        }
        // 更新已有记录
        existing.setQuietHoursStart(preference.getQuietHoursStart());
        existing.setQuietHoursEnd(preference.getQuietHoursEnd());
        existing.setDigestMode(preference.getDigestMode() == null ? 0 : preference.getDigestMode());
        if (preference.getProviderTraceId() != null) {
            existing.setProviderTraceId(preference.getProviderTraceId());
        }
        preferenceMapper.updateById(existing);
        log.info("[FlowNotifyPreference] 更新偏好: tenant={} userId={} quiet={}~{} digest={}",
                effectiveTenant, userId,
                existing.getQuietHoursStart(), existing.getQuietHoursEnd(),
                existing.getDigestMode());
        return existing.getId();
    }

    @Override
    public boolean isQuietHours(String tenantId, String userId) {
        FlowNotifyPreferenceDO pref = getOrCreate(tenantId, userId);
        return isInQuietHours(LocalTime.now(), pref.getQuietHoursStart(), pref.getQuietHoursEnd());
    }

    /**
     * 判断给定时刻是否落在免打扰时段内（包测试用）。
     *
     * <p>规则：
     * <ul>
     *   <li>start 或 end 为空 / 格式非法 → false（fail-open，不静默）</li>
     *   <li>start == end → false（零长度窗口）</li>
     *   <li>start &lt; end（同日窗口，如 12:00→14:00）→ now ∈ [start, end)</li>
     *   <li>start &gt; end（跨午夜窗口，如 22:00→08:00）→ now ≥ start || now &lt; end</li>
     * </ul>
     *
     * @param now   当前时刻
     * @param start 免打扰开始时间（HH:mm），null/空/格式非法返回 false
     * @param end   免打扰结束时间（HH:mm），null/空/格式非法返回 false
     * @return true 表示在免打扰时段内
     */
    boolean isInQuietHours(LocalTime now, String start, String end) {
        if (!StringUtils.hasText(start) || !StringUtils.hasText(end)) {
            return false;
        }
        LocalTime startTime = parseTime(start);
        LocalTime endTime = parseTime(end);
        if (startTime == null || endTime == null) {
            return false;
        }
        if (startTime.equals(endTime)) {
            return false;
        }
        if (startTime.isBefore(endTime)) {
            // 同日窗口：12:00→14:00
            return !now.isBefore(startTime) && now.isBefore(endTime);
        }
        // 跨午夜窗口：22:00→08:00 → now >= 22:00 || now < 08:00
        return !now.isBefore(startTime) || now.isBefore(endTime);
    }

    @Override
    public boolean shouldDefer(String tenantId, String userId) {
        FlowNotifyPreferenceDO pref = getOrCreate(tenantId, userId);
        if (pref.getDigestMode() == null || pref.getDigestMode() != DIGEST_MODE_AGGREGATE) {
            return false;
        }
        return isInQuietHours(LocalTime.now(), pref.getQuietHoursStart(), pref.getQuietHoursEnd());
    }

    // ============================== 内部辅助 ==============================

    /**
     * 解析 HH:mm 格式时间字符串，失败返回 null（fail-open）
     */
    private LocalTime parseTime(String hhmm) {
        if (hhmm == null) {
            return null;
        }
        try {
            return LocalTime.parse(hhmm);
        } catch (DateTimeParseException e) {
            log.warn("[FlowNotifyPreference] 时间格式非法（应为 HH:mm）: {}", hhmm);
            return null;
        }
    }

    /**
     * 校验时间格式，非空但格式非法时抛异常
     */
    private void validateTimeFormat(String hhmm, String fieldName) {
        if (!StringUtils.hasText(hhmm)) {
            return;
        }
        try {
            LocalTime.parse(hhmm);
        } catch (DateTimeParseException e) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.workflow.msg_b8c9d0e1", fieldName);
        }
    }
}
