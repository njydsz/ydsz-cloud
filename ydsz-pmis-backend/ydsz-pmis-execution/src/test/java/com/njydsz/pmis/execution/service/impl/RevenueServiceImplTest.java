package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.RevenueCreateDTO;
import com.njydsz.pmis.execution.entity.RevenueDO;
import com.njydsz.pmis.execution.mapper.RevenueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RevenueServiceImpl 收入确认服务测试")
class RevenueServiceImplTest {

    private RevenueMapper mapper;
    private RevenueServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(RevenueMapper.class);
        service = new RevenueServiceImpl(mapper);
    }

    @Test
    @DisplayName("create - 缺少必填")
    void createMissing() {
        RevenueCreateDTO dto = new RevenueCreateDTO();
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create - 收入方法非法")
    void createInvalidMethod() {
        RevenueCreateDTO dto = new RevenueCreateDTO();
        dto.setRevenueCode("R-1");
        dto.setContractId(1L);
        dto.setInitiationId(1L);
        dto.setAmount(new BigDecimal("100"));
        dto.setRecognitionMethod("UNKNOWN_METHOD");
        dto.setPeriod("2026-06");
        dto.setRecognitionDate(LocalDate.now());
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 成功")
    void createOk() {
        when(mapper.selectByCode("R-1")).thenReturn(null);
        when(mapper.insert(any(RevenueDO.class))).thenAnswer(inv -> {
            RevenueDO r = inv.getArgument(0);
            r.setId(7L);
            return 1;
        });
        RevenueCreateDTO dto = new RevenueCreateDTO();
        dto.setRevenueCode("R-1");
        dto.setContractId(1L);
        dto.setInitiationId(1L);
        dto.setAmount(new BigDecimal("100000"));
        dto.setRecognitionMethod("MILESTONE");
        dto.setPeriod("2026-06");
        dto.setRecognitionDate(LocalDate.now());
        Long id = service.create(dto);
        assertThat(id).isEqualTo(7L);
    }

    @Test
    @DisplayName("confirm - 只能确认 DRAFT")
    void confirmOnlyDraft() {
        RevenueDO r = new RevenueDO();
        r.setId(1L);
        r.setStatus("CONFIRMED");
        when(mapper.selectById(1L)).thenReturn(r);
        assertThatThrownBy(() -> service.confirm(1L, 100L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("reverse - 只能冲销 CONFIRMED")
    void reverseOnlyConfirmed() {
        RevenueDO r = new RevenueDO();
        r.setId(1L);
        r.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(r);
        assertThatThrownBy(() -> service.reverse(1L))
                .isInstanceOf(BizException.class);
    }
}
