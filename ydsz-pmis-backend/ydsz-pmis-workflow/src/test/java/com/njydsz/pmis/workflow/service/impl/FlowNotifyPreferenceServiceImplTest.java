package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.entity.FlowNotifyPreferenceDO;
import com.njydsz.pmis.workflow.mapper.FlowNotifyPreferenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FlowNotifyPreferenceServiceImpl} 单元测试。
 *
 * <p>重点测试免打扰时段判断逻辑（同日窗口 / 跨午夜窗口 / 边界值 / 格式非法 fail-open），
 * 以及 digestMode 与 quietHours 的组合语义。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@DisplayName("FlowNotifyPreferenceServiceImpl 免打扰时段测试")
@ExtendWith(MockitoExtension.class)
class FlowNotifyPreferenceServiceImplTest {

    @Mock
    private FlowNotifyPreferenceMapper preferenceMapper;

    @InjectMocks
    private FlowNotifyPreferenceServiceImpl service;

    // ============================== isInQuietHours 时间逻辑测试 ==============================

    @Nested
    @DisplayName("isInQuietHours 时间区间判断")
    class IsInQuietHoursTest {

        @Test
        @DisplayName("同日窗口 12:00→14:00：区间内返回 true")
        void sameDayWindow_inside_returnsTrue() {
            assertTrue(service.isInQuietHours(LocalTime.of(13, 0), "12:00", "14:00"));
        }

        @Test
        @DisplayName("同日窗口 12:00→14:00：区间外返回 false")
        void sameDayWindow_outside_returnsFalse() {
            assertFalse(service.isInQuietHours(LocalTime.of(11, 59), "12:00", "14:00"));
            assertFalse(service.isInQuietHours(LocalTime.of(14, 0), "12:00", "14:00"));
        }

        @Test
        @DisplayName("同日窗口：start 边界包含（闭区间），end 边界不包含（开区间）")
        void sameDayWindow_boundary() {
            // now == start → true（闭区间）
            assertTrue(service.isInQuietHours(LocalTime.of(12, 0), "12:00", "14:00"));
            // now == end → false（开区间）
            assertFalse(service.isInQuietHours(LocalTime.of(14, 0), "12:00", "14:00"));
        }

        @Test
        @DisplayName("跨午夜窗口 22:00→08:00：午夜前（23:00）返回 true")
        void crossMidnightWindow_beforeMidnight_returnsTrue() {
            assertTrue(service.isInQuietHours(LocalTime.of(23, 0), "22:00", "08:00"));
        }

        @Test
        @DisplayName("跨午夜窗口 22:00→08:00：午夜后（03:00）返回 true")
        void crossMidnightWindow_afterMidnight_returnsTrue() {
            assertTrue(service.isInQuietHours(LocalTime.of(3, 0), "22:00", "08:00"));
        }

        @Test
        @DisplayName("跨午夜窗口 22:00→08:00：区间外（10:00 / 21:00）返回 false")
        void crossMidnightWindow_outside_returnsFalse() {
            assertFalse(service.isInQuietHours(LocalTime.of(10, 0), "22:00", "08:00"));
            assertFalse(service.isInQuietHours(LocalTime.of(21, 0), "22:00", "08:00"));
        }

        @Test
        @DisplayName("跨午夜窗口边界：start=22:00 包含，end=08:00 不包含")
        void crossMidnightWindow_boundary() {
            // now == start → true
            assertTrue(service.isInQuietHours(LocalTime.of(22, 0), "22:00", "08:00"));
            // now == end → false
            assertFalse(service.isInQuietHours(LocalTime.of(8, 0), "22:00", "08:00"));
        }

        @Test
        @DisplayName("start == end（零长度窗口）返回 false")
        void zeroLengthWindow_returnsFalse() {
            assertFalse(service.isInQuietHours(LocalTime.of(12, 0), "12:00", "12:00"));
        }

        @Test
        @DisplayName("start 为空返回 false")
        void nullStart_returnsFalse() {
            assertFalse(service.isInQuietHours(LocalTime.of(13, 0), null, "14:00"));
            assertFalse(service.isInQuietHours(LocalTime.of(13, 0), "", "14:00"));
            assertFalse(service.isInQuietHours(LocalTime.of(13, 0), "  ", "14:00"));
        }

        @Test
        @DisplayName("end 为空返回 false")
        void nullEnd_returnsFalse() {
            assertFalse(service.isInQuietHours(LocalTime.of(13, 0), "12:00", null));
            assertFalse(service.isInQuietHours(LocalTime.of(13, 0), "12:00", ""));
        }

        @Test
        @DisplayName("格式非法时 fail-open 返回 false（不静默）")
        void invalidFormat_returnsFalse() {
            assertFalse(service.isInQuietHours(LocalTime.of(13, 0), "25:00", "14:00"));
            assertFalse(service.isInQuietHours(LocalTime.of(13, 0), "12:00", "abc"));
            assertFalse(service.isInQuietHours(LocalTime.of(13, 0), "12:60", "14:00"));
        }

        @Test
        @DisplayName("全天免打扰 00:00→23:59")
        void allDayQuiet() {
            assertTrue(service.isInQuietHours(LocalTime.of(0, 0), "00:00", "23:59"));
            assertTrue(service.isInQuietHours(LocalTime.of(12, 0), "00:00", "23:59"));
            // 23:59 是 end 边界（开区间），返回 false
            assertFalse(service.isInQuietHours(LocalTime.of(23, 59), "00:00", "23:59"));
        }
    }

    // ============================== shouldDefer 组合语义测试 ==============================

    @Nested
    @DisplayName("shouldDefer digestMode 组合语义")
    class ShouldDeferTest {

        @Test
        @DisplayName("digestMode=1 + 免打扰时段内 → true")
        void aggregateMode_inQuietHours_returnsTrue() {
            FlowNotifyPreferenceDO pref = new FlowNotifyPreferenceDO();
            pref.setDigestMode(1);
            pref.setQuietHoursStart("00:00");
            pref.setQuietHoursEnd("23:59");
            when(preferenceMapper.selectByUserId(eq("1"), eq("u1"))).thenReturn(pref);

            assertTrue(service.shouldDefer("1", "u1"));
        }

        @Test
        @DisplayName("digestMode=0（立即投递）+ 免打扰时段内 → false")
        void immediateMode_inQuietHours_returnsFalse() {
            FlowNotifyPreferenceDO pref = new FlowNotifyPreferenceDO();
            pref.setDigestMode(0);
            pref.setQuietHoursStart("00:00");
            pref.setQuietHoursEnd("23:59");
            when(preferenceMapper.selectByUserId(eq("1"), eq("u1"))).thenReturn(pref);

            assertFalse(service.shouldDefer("1", "u1"));
        }

        @Test
        @DisplayName("digestMode=null（未设置）→ false")
        void nullDigestMode_returnsFalse() {
            FlowNotifyPreferenceDO pref = new FlowNotifyPreferenceDO();
            pref.setDigestMode(null);
            when(preferenceMapper.selectByUserId(eq("1"), eq("u1"))).thenReturn(pref);

            assertFalse(service.shouldDefer("1", "u1"));
        }

        @Test
        @DisplayName("digestMode=1 + 未配置 quietHours → false")
        void aggregateMode_noQuietHours_returnsFalse() {
            FlowNotifyPreferenceDO pref = new FlowNotifyPreferenceDO();
            pref.setDigestMode(1);
            pref.setQuietHoursStart(null);
            pref.setQuietHoursEnd(null);
            when(preferenceMapper.selectByUserId(eq("1"), eq("u1"))).thenReturn(pref);

            assertFalse(service.shouldDefer("1", "u1"));
        }

        @Test
        @DisplayName("无偏好记录（默认 digestMode=0）→ false")
        void noPreference_returnsFalse() {
            when(preferenceMapper.selectByUserId(eq("1"), eq("u1"))).thenReturn(null);

            assertFalse(service.shouldDefer("1", "u1"));
        }
    }

    // ============================== getOrCreate 测试 ==============================

    @Nested
    @DisplayName("getOrCreate 查询/默认")
    class GetOrCreateTest {

        @Test
        @DisplayName("存在记录 → 返回数据库记录")
        void existing_returnsRecord() {
            FlowNotifyPreferenceDO pref = new FlowNotifyPreferenceDO();
            pref.setId("p1");
            pref.setDigestMode(1);
            pref.setQuietHoursStart("22:00");
            pref.setQuietHoursEnd("08:00");
            when(preferenceMapper.selectByUserId(eq("1"), eq("u1"))).thenReturn(pref);

            FlowNotifyPreferenceDO result = service.getOrCreate("1", "u1");
            assertEquals("p1", result.getId());
            assertEquals(1, result.getDigestMode());
        }

        @Test
        @DisplayName("不存在记录 → 返回默认实例（digestMode=0，不写库）")
        void notExisting_returnsDefault() {
            when(preferenceMapper.selectByUserId(eq("1"), eq("u1"))).thenReturn(null);

            FlowNotifyPreferenceDO result = service.getOrCreate("1", "u1");
            assertNotNull(result);
            assertEquals("1", result.getTenantId());
            assertEquals("u1", result.getUserId());
            assertEquals(0, result.getDigestMode());
            verify(preferenceMapper, never()).insert(any(FlowNotifyPreferenceDO.class));
        }

        @Test
        @DisplayName("tenantId=null → 默认 '1'")
        void nullTenant_defaultsToOne() {
            when(preferenceMapper.selectByUserId(eq("1"), eq("u1"))).thenReturn(null);

            FlowNotifyPreferenceDO result = service.getOrCreate(null, "u1");
            assertEquals("1", result.getTenantId());
        }
    }

    // ============================== save 测试 ==============================

    @Nested
    @DisplayName("save 新增/更新/校验")
    class SaveTest {

        @Test
        @DisplayName("userId 为空 → 抛 BizException")
        void emptyUserId_throwsException() {
            FlowNotifyPreferenceDO pref = new FlowNotifyPreferenceDO();
            assertThrows(BizException.class, () -> service.save("1", "", pref));
            assertThrows(BizException.class, () -> service.save("1", null, pref));
        }

        @Test
        @DisplayName("quietHoursStart 格式非法 → 抛 BizException")
        void invalidStartTime_throwsException() {
            FlowNotifyPreferenceDO pref = new FlowNotifyPreferenceDO();
            pref.setQuietHoursStart("25:00");
            assertThrows(BizException.class, () -> service.save("1", "u1", pref));
        }

        @Test
        @DisplayName("quietHoursEnd 格式非法 → 抛 BizException")
        void invalidEndTime_throwsException() {
            FlowNotifyPreferenceDO pref = new FlowNotifyPreferenceDO();
            pref.setQuietHoursEnd("abc");
            assertThrows(BizException.class, () -> service.save("1", "u1", pref));
        }

        @Test
        @DisplayName("新增偏好 → 调用 insert")
        void saveNew_callsInsert() {
            FlowNotifyPreferenceDO pref = new FlowNotifyPreferenceDO();
            pref.setQuietHoursStart("22:00");
            pref.setQuietHoursEnd("08:00");
            pref.setDigestMode(1);
            when(preferenceMapper.selectByUserId(eq("1"), eq("u1"))).thenReturn(null);

            service.save("1", "u1", pref);

            verify(preferenceMapper).insert(any(FlowNotifyPreferenceDO.class));
            verify(preferenceMapper, never()).updateById(any(FlowNotifyPreferenceDO.class));
        }

        @Test
        @DisplayName("更新已有偏好 → 调用 updateById")
        void saveExisting_callsUpdate() {
            FlowNotifyPreferenceDO existing = new FlowNotifyPreferenceDO();
            existing.setId("p1");
            existing.setDigestMode(0);
            when(preferenceMapper.selectByUserId(eq("1"), eq("u1"))).thenReturn(existing);

            FlowNotifyPreferenceDO pref = new FlowNotifyPreferenceDO();
            pref.setQuietHoursStart("23:00");
            pref.setQuietHoursEnd("07:00");
            pref.setDigestMode(1);

            service.save("1", "u1", pref);

            verify(preferenceMapper).updateById(any(FlowNotifyPreferenceDO.class));
            verify(preferenceMapper, never()).insert(any(FlowNotifyPreferenceDO.class));
        }

        @Test
        @DisplayName("digestMode=null 时默认 0")
        void saveNullDigestMode_defaultsToZero() {
            FlowNotifyPreferenceDO pref = new FlowNotifyPreferenceDO();
            pref.setQuietHoursStart("22:00");
            pref.setQuietHoursEnd("08:00");
            pref.setDigestMode(null);
            when(preferenceMapper.selectByUserId(eq("1"), eq("u1"))).thenReturn(null);

            service.save("1", "u1", pref);

            assertEquals(0, pref.getDigestMode());
        }
    }
}
