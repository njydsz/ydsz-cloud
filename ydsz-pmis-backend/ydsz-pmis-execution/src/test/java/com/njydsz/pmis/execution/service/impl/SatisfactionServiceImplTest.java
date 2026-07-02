package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.SatisfactionCreateDTO;
import com.njydsz.pmis.execution.entity.SatisfactionDO;
import com.njydsz.pmis.execution.enums.SatisfactionLevel;
import com.njydsz.pmis.execution.mapper.SatisfactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SatisfactionServiceImpl 单元测试
 */
@DisplayName("SatisfactionServiceImpl 满意度测试")
class SatisfactionServiceImplTest {

    private SatisfactionMapper mapper;
    private SatisfactionServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(SatisfactionMapper.class);
        service = new SatisfactionServiceImpl(mapper);
    }

    @Test
    @DisplayName("submit DTO 为空应抛 BAD_REQUEST")
    void submit_null() {
        assertThatThrownBy(() -> service.submit(null))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("submit initiationId 为空应抛 BAD_REQUEST")
    void submit_nullInitiation() {
        SatisfactionCreateDTO dto = new SatisfactionCreateDTO();
        dto.setScore(5);
        assertThatThrownBy(() -> service.submit(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("submit 评分缺失应抛 BAD_REQUEST")
    void submit_noScore() {
        SatisfactionCreateDTO dto = new SatisfactionCreateDTO();
        dto.setInitiationId(1L);
        assertThatThrownBy(() -> service.submit(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("submit 评分 0 应抛 BAD_REQUEST")
    void submit_zeroScore() {
        SatisfactionCreateDTO dto = validDto();
        dto.setScore(0);
        assertThatThrownBy(() -> service.submit(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("submit 评分 6 应抛 BAD_REQUEST")
    void submit_tooHighScore() {
        SatisfactionCreateDTO dto = validDto();
        dto.setScore(6);
        assertThatThrownBy(() -> service.submit(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("submit 专业度 0 应抛 BAD_REQUEST")
    void submit_invalidProfessionalism() {
        SatisfactionCreateDTO dto = validDto();
        dto.setProfessionalism(0);
        assertThatThrownBy(() -> service.submit(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("submit 及时性 6 应抛 BAD_REQUEST")
    void submit_invalidTimeliness() {
        SatisfactionCreateDTO dto = validDto();
        dto.setTimeliness(6);
        assertThatThrownBy(() -> service.submit(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("submit 质量越界应抛 BAD_REQUEST")
    void submit_invalidQuality() {
        SatisfactionCreateDTO dto = validDto();
        dto.setQuality(10);
        assertThatThrownBy(() -> service.submit(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("submit 态度越界应抛 BAD_REQUEST")
    void submit_invalidAttitude() {
        SatisfactionCreateDTO dto = validDto();
        dto.setAttitude(0);
        assertThatThrownBy(() -> service.submit(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("submit 5 星 VERY_SATISFIED 不跟进")
    void submit_5star() {
        SatisfactionCreateDTO dto = validDto();
        dto.setScore(5);
        when(mapper.insert(any(SatisfactionDO.class))).thenAnswer(inv -> {
            SatisfactionDO arg = inv.getArgument(0);
            arg.setId(100L);
            return 1;
        });
        Long id = service.submit(dto);
        assertThat(id).isEqualTo(100L);
        ArgumentCaptor<SatisfactionDO> cap = ArgumentCaptor.forClass(SatisfactionDO.class);
        verify(mapper).insert(cap.capture());
        SatisfactionDO saved = cap.getValue();
        assertThat(saved.getLevel()).isEqualTo(SatisfactionLevel.VERY_SATISFIED.getCode());
        assertThat(saved.getFollowUp()).isFalse();
    }

    @Test
    @DisplayName("submit 3 星 NEUTRAL 不默认跟进")
    void submit_3star() {
        SatisfactionCreateDTO dto = validDto();
        dto.setScore(3);
        when(mapper.insert(any(SatisfactionDO.class))).thenAnswer(inv -> {
            SatisfactionDO arg = inv.getArgument(0);
            arg.setId(101L);
            return 1;
        });
        service.submit(dto);
        ArgumentCaptor<SatisfactionDO> cap = ArgumentCaptor.forClass(SatisfactionDO.class);
        verify(mapper).insert(cap.capture());
        SatisfactionDO saved = cap.getValue();
        assertThat(saved.getLevel()).isEqualTo(SatisfactionLevel.NEUTRAL.getCode());
        assertThat(saved.getFollowUp()).isFalse();
    }

    @Test
    @DisplayName("submit 1 星 VERY_DISSATISFIED 默认跟进")
    void submit_1star_followUp() {
        SatisfactionCreateDTO dto = validDto();
        dto.setScore(1);
        when(mapper.insert(any(SatisfactionDO.class))).thenAnswer(inv -> {
            SatisfactionDO arg = inv.getArgument(0);
            arg.setId(102L);
            return 1;
        });
        service.submit(dto);
        ArgumentCaptor<SatisfactionDO> cap = ArgumentCaptor.forClass(SatisfactionDO.class);
        verify(mapper).insert(cap.capture());
        SatisfactionDO saved = cap.getValue();
        assertThat(saved.getLevel()).isEqualTo(SatisfactionLevel.VERY_DISSATISFIED.getCode());
        assertThat(saved.getFollowUp()).isTrue();
    }

    @Test
    @DisplayName("submit 2 星 DISSATISFIED 默认跟进")
    void submit_2star_followUp() {
        SatisfactionCreateDTO dto = validDto();
        dto.setScore(2);
        when(mapper.insert(any(SatisfactionDO.class))).thenAnswer(inv -> {
            SatisfactionDO arg = inv.getArgument(0);
            arg.setId(103L);
            return 1;
        });
        service.submit(dto);
        ArgumentCaptor<SatisfactionDO> cap = ArgumentCaptor.forClass(SatisfactionDO.class);
        verify(mapper).insert(cap.capture());
        SatisfactionDO saved = cap.getValue();
        assertThat(saved.getFollowUp()).isTrue();
    }

    @Test
    @DisplayName("markFollowUp id 为空应抛 BAD_REQUEST")
    void markFollowUp_null() {
        assertThatThrownBy(() -> service.markFollowUp(null, "note"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("markFollowUp 不存在应抛 NOT_FOUND")
    void markFollowUp_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.markFollowUp(99L, "note"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("markFollowUp 正常应设置 followUp=true")
    void markFollowUp_ok() {
        SatisfactionDO s = new SatisfactionDO();
        s.setId(1L);
        s.setFollowUp(false);
        when(mapper.selectById(1L)).thenReturn(s);
        service.markFollowUp(1L, "客户不满");
        ArgumentCaptor<SatisfactionDO> cap = ArgumentCaptor.forClass(SatisfactionDO.class);
        verify(mapper).updateById(cap.capture());
        SatisfactionDO saved = cap.getValue();
        assertThat(saved.getFollowUp()).isTrue();
        assertThat(saved.getFollowUpNote()).isEqualTo("客户不满");
    }

    @Test
    @DisplayName("closeFollowUp 正常")
    void closeFollowUp_ok() {
        SatisfactionDO s = new SatisfactionDO();
        s.setId(1L);
        s.setFollowUp(true);
        when(mapper.selectById(1L)).thenReturn(s);
        service.closeFollowUp(1L);
        ArgumentCaptor<SatisfactionDO> cap = ArgumentCaptor.forClass(SatisfactionDO.class);
        verify(mapper).updateById(cap.capture());
        assertThat(cap.getValue().getFollowUp()).isFalse();
    }

    private SatisfactionCreateDTO validDto() {
        SatisfactionCreateDTO dto = new SatisfactionCreateDTO();
        dto.setInitiationId(1L);
        dto.setScore(4);
        dto.setProfessionalism(5);
        dto.setTimeliness(4);
        dto.setQuality(4);
        dto.setAttitude(5);
        return dto;
    }
}
