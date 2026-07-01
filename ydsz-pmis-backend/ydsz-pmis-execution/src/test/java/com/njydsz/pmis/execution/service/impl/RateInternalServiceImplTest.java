package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.RateInternalCreateDTO;
import com.njydsz.pmis.execution.entity.RateInternalDO;
import com.njydsz.pmis.execution.mapper.RateInternalMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RateInternalServiceImpl 对内成本费率")
class RateInternalServiceImplTest {

    private RateInternalMapper mapper;
    private RateInternalServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(RateInternalMapper.class);
        service = new RateInternalServiceImpl(mapper);
    }

    @Test
    @DisplayName("create 缺 rateCode")
    void createMissing() {
        RateInternalCreateDTO dto = new RateInternalCreateDTO();
        dto.setLevelCode("L8");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 重复编号")
    void createDuplicate() {
        when(mapper.selectByCode("RI-1")).thenReturn(new RateInternalDO());
        RateInternalCreateDTO dto = new RateInternalCreateDTO();
        dto.setRateCode("RI-1");
        dto.setLevelCode("L8");
        dto.setBillingUnit("DAY");
        dto.setCostAmount(new BigDecimal("2800"));
        dto.setEffectiveDate(LocalDate.now());
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 成功")
    void createOk() {
        when(mapper.selectByCode("RI-1")).thenReturn(null);
        when(mapper.insert(any(RateInternalDO.class))).thenAnswer(inv -> {
            ((RateInternalDO) inv.getArgument(0)).setId(22L);
            return 1;
        });
        RateInternalCreateDTO dto = new RateInternalCreateDTO();
        dto.setRateCode("RI-1");
        dto.setLevelCode("L8");
        dto.setBillingUnit("DAY");
        dto.setCostAmount(new BigDecimal("2800"));
        dto.setEffectiveDate(LocalDate.now());
        Long id = service.create(dto);
        assertThat(id).isEqualTo(22L);
    }

    @Test
    @DisplayName("update 部分字段")
    void updatePartial() {
        RateInternalDO existing = new RateInternalDO();
        existing.setId(1L);
        existing.setCostAmount(new BigDecimal("2800"));
        when(mapper.selectById(1L)).thenReturn(existing);
        when(mapper.updateById(any(RateInternalDO.class))).thenReturn(1);
        RateInternalCreateDTO dto = new RateInternalCreateDTO();
        dto.setCostAmount(new BigDecimal("3000"));
        service.update(1L, dto);
        verify(mapper).updateById(any(RateInternalDO.class));
    }

    @Test
    @DisplayName("matchEffective null 兜底")
    void matchSafe() {
        when(mapper.matchEffective(any(), any(), any())).thenReturn(null);
        assertThat(service.matchEffective(null, null, null)).isNull();
        assertThat(service.matchEffective("L8", null, null)).isNull();
    }

    @Test
    @DisplayName("listByLevelAndDept null 安全")
    void listSafe() {
        when(mapper.selectByLevelAndDept("L8", null)).thenReturn(List.of());
        assertThat(service.listByLevelAndDept("L8", null)).isEmpty();
        assertThat(service.listByLevelAndDept(null, 1L)).isEmpty();
    }
}
