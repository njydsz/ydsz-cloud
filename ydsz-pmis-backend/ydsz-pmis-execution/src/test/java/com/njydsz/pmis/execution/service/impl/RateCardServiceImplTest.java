package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.RateCardCreateDTO;
import com.njydsz.pmis.execution.entity.RateCardDO;
import com.njydsz.pmis.execution.mapper.RateCardMapper;
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

@DisplayName("RateCardServiceImpl 对外报价费率")
class RateCardServiceImplTest {

    private RateCardMapper mapper;
    private RateCardServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(RateCardMapper.class);
        service = new RateCardServiceImpl(mapper);
    }

    @Test
    @DisplayName("create 缺 rateCode 必填")
    void createMissingCode() {
        RateCardCreateDTO dto = new RateCardCreateDTO();
        dto.setLevelCode("L8");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 重复编号")
    void createDuplicate() {
        when(mapper.selectByCode("RC-1")).thenReturn(new RateCardDO());
        RateCardCreateDTO dto = new RateCardCreateDTO();
        dto.setRateCode("RC-1");
        dto.setLevelCode("L8");
        dto.setBillingUnit("DAY");
        dto.setRateAmount(new BigDecimal("4000"));
        dto.setEffectiveDate(LocalDate.now());
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 负金额校验")
    void createNegativeAmount() {
        RateCardCreateDTO dto = new RateCardCreateDTO();
        dto.setRateCode("RC-1");
        dto.setLevelCode("L8");
        dto.setBillingUnit("DAY");
        dto.setRateAmount(new BigDecimal("-100"));
        dto.setEffectiveDate(LocalDate.now());
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 成功")
    void createOk() {
        when(mapper.selectByCode("RC-1")).thenReturn(null);
        when(mapper.insert(any(RateCardDO.class))).thenAnswer(inv -> {
            ((RateCardDO) inv.getArgument(0)).setId(11L);
            return 1;
        });
        RateCardCreateDTO dto = new RateCardCreateDTO();
        dto.setRateCode("RC-1");
        dto.setLevelCode("L8");
        dto.setBillingUnit("DAY");
        dto.setRateAmount(new BigDecimal("4000"));
        dto.setEffectiveDate(LocalDate.now());
        Long id = service.create(dto);
        assertThat(id).isEqualTo(11L);
    }

    @Test
    @DisplayName("update 部分字段")
    void updatePartial() {
        RateCardDO existing = new RateCardDO();
        existing.setId(1L);
        existing.setLevelCode("L8");
        existing.setRateAmount(new BigDecimal("4000"));
        when(mapper.selectById(1L)).thenReturn(existing);
        when(mapper.updateById(any(RateCardDO.class))).thenReturn(1);
        RateCardCreateDTO dto = new RateCardCreateDTO();
        dto.setRateAmount(new BigDecimal("4500"));
        dto.setStatus("INACTIVE");
        service.update(1L, dto);
        verify(mapper).updateById(any(RateCardDO.class));
    }

    @Test
    @DisplayName("matchEffective null/空 安全降级")
    void matchSafe() {
        when(mapper.matchEffective(any(), any(), any(), any())).thenReturn(null);
        assertThat(service.matchEffective(null, null, null, null)).isNull();
        assertThat(service.matchEffective("L8", null, null, null)).isNull();
    }

    @Test
    @DisplayName("getById 不存在抛异常")
    void getByIdNotFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("listByLevel 空/null 安全")
    void listSafe() {
        when(mapper.selectByLevel("L8")).thenReturn(List.of());
        assertThat(service.listByLevel("L8")).isEmpty();
        assertThat(service.listByLevel(null)).isEmpty();
        assertThat(service.listByLevel("")).isEmpty();
    }
}
