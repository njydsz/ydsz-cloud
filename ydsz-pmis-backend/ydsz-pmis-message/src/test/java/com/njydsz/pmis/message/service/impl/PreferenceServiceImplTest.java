package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.message.constant.MessageConstants;
import com.njydsz.pmis.message.dto.PreferenceUpsertDTO;
import com.njydsz.pmis.message.entity.MsgPreferenceDO;
import com.njydsz.pmis.message.mapper.MsgPreferenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PreferenceServiceImpl} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("PreferenceServiceImpl 偏好服务测试")
@ExtendWith(MockitoExtension.class)
class PreferenceServiceImplTest {

    @Mock
    private MsgPreferenceMapper msgPreferenceMapper;

    @InjectMocks
    private PreferenceServiceImpl preferenceService;

    @Test
    @DisplayName("upsert 不存在时新建")
    void upsertShouldInsertWhenAbsent() {
        PreferenceUpsertDTO dto = new PreferenceUpsertDTO();
        dto.setUserId("u1");
        dto.setChannel("SMS");
        dto.setBizType("ALERT");
        when(msgPreferenceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgPreferenceDO result = preferenceService.upsert(dto);

        assertEquals("u1", result.getUserId());
        assertEquals("ALERT", result.getBizType());
        verify(msgPreferenceMapper).insert(any(MsgPreferenceDO.class));
    }

    @Test
    @DisplayName("upsert 存在时更新")
    void upsertShouldUpdateWhenExists() {
        PreferenceUpsertDTO dto = new PreferenceUpsertDTO();
        dto.setUserId("u1");
        dto.setChannel("SMS");
        dto.setDailyLimit(50);
        MsgPreferenceDO existing = new MsgPreferenceDO();
        existing.setUserId("u1");
        existing.setDailyLimit(10);
        when(msgPreferenceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        MsgPreferenceDO result = preferenceService.upsert(dto);

        assertEquals(50, result.getDailyLimit());
        verify(msgPreferenceMapper).updateById(existing);
    }

    @Test
    @DisplayName("getByUser 精确 bizType 命中时返回")
    void getByUserShouldReturnExactBizType() {
        MsgPreferenceDO pref = new MsgPreferenceDO();
        pref.setBizType("ALERT");
        when(msgPreferenceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pref);

        MsgPreferenceDO result = preferenceService.getByUser("u1", "SMS", "ALERT");
        assertEquals("ALERT", result.getBizType());
    }

    @Test
    @DisplayName("getByUser 精确 bizType 缺失时回退默认")
    void getByUserShouldFallbackToDefault() {
        MsgPreferenceDO def = new MsgPreferenceDO();
        def.setBizType(MessageConstants.DEFAULT_BIZ_TYPE);
        when(msgPreferenceMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null)
                .thenReturn(def);

        MsgPreferenceDO result = preferenceService.getByUser("u1", "SMS", "ALERT");
        assertNotNull(result);
        assertEquals(MessageConstants.DEFAULT_BIZ_TYPE, result.getBizType());
    }

    @Test
    @DisplayName("getByUser 均无时返回 null")
    void getByUserShouldReturnNullWhenAllAbsent() {
        when(msgPreferenceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgPreferenceDO result = preferenceService.getByUser("u1", "SMS", "ALERT");
        assertNull(result);
    }
}
