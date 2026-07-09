package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.PartTimeRateCreateDTO;
import com.njydsz.pmis.userinfo.dto.PartTimeRateUpdateDTO;
import com.njydsz.pmis.userinfo.entity.PartTimeRateDO;
import com.njydsz.pmis.userinfo.mapper.PartTimeRateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PartTimeRateServiceImpl 单元测试
 *
 * <p>覆盖兼职职级费率 CRUD 核心行为：月薪+商业保险+差旅报销+差旅补贴自动计算 totalCost、
 * 级别编码+版本唯一性校验、级别段位校验、日期校验、生效费率匹配。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PartTimeRateServiceImpl 兼职职级费率服务测试")
class PartTimeRateServiceImplTest {

    @Mock
    private PartTimeRateMapper partTimeRateMapper;

    @InjectMocks
    private PartTimeRateServiceImpl service;

    // ==================== create ====================

    @Test
    @DisplayName("创建成功: 自动计算 totalCost = monthlySalary + commercialInsurance + travelReimbursement + travelAllowance")
    void create_success_calculatesTotalCost() {
        PartTimeRateCreateDTO dto = new PartTimeRateCreateDTO();
        dto.setRateCode("P5");
        dto.setRateName("兼职高级工程师");
        dto.setLevelSegment("MIDDLE");
        dto.setMonthlySalary(new BigDecimal("6000"));
        dto.setCommercialInsurance(new BigDecimal("80"));
        dto.setTravelReimbursement(new BigDecimal("500"));
        dto.setTravelAllowance(new BigDecimal("300"));
        dto.setEffectiveDate(LocalDate.of(2026, 1, 1));
        when(partTimeRateMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            inv.<PartTimeRateDO>getArgument(0).setId("PTR-GENERATED");
            return 1;
        }).when(partTimeRateMapper).insert(any(PartTimeRateDO.class));

        String id = service.create(dto);

        ArgumentCaptor<PartTimeRateDO> captor = ArgumentCaptor.forClass(PartTimeRateDO.class);
        verify(partTimeRateMapper).insert(captor.capture());
        PartTimeRateDO saved = captor.getValue();
        assertEquals("P5", saved.getRateCode());
        assertEquals(new BigDecimal("6880.00"), saved.getTotalCost());
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals(1, saved.getVersion());
        assertEquals("PTR-GENERATED", id);
    }

    @Test
    @DisplayName("创建成功: commercialInsurance 为 null 时 totalCost = monthlySalary")
    void create_success_nullInsurance() {
        PartTimeRateCreateDTO dto = new PartTimeRateCreateDTO();
        dto.setRateCode("P1");
        dto.setRateName("兼职助理工程师");
        dto.setLevelSegment("PRIMARY");
        dto.setMonthlySalary(new BigDecimal("3000"));
        dto.setEffectiveDate(LocalDate.of(2026, 1, 1));
        when(partTimeRateMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            inv.<PartTimeRateDO>getArgument(0).setId("PTR-2");
            return 1;
        }).when(partTimeRateMapper).insert(any(PartTimeRateDO.class));

        service.create(dto);

        ArgumentCaptor<PartTimeRateDO> captor = ArgumentCaptor.forClass(PartTimeRateDO.class);
        verify(partTimeRateMapper).insert(captor.capture());
        assertEquals(new BigDecimal("3000.00"), captor.getValue().getTotalCost());
    }

    @Test
    @DisplayName("创建失败: monthlySalary 不为正数抛 BAD_REQUEST")
    void create_invalidSalary() {
        PartTimeRateCreateDTO dto = new PartTimeRateCreateDTO();
        dto.setRateCode("P1");
        dto.setRateName("兼职助理工程师");
        dto.setLevelSegment("PRIMARY");
        dto.setMonthlySalary(BigDecimal.ZERO);
        dto.setEffectiveDate(LocalDate.of(2026, 1, 1));

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(partTimeRateMapper, never()).insert(any(PartTimeRateDO.class));
    }

    @Test
    @DisplayName("创建失败: 级别段位非法抛 BAD_REQUEST")
    void create_invalidSegment() {
        PartTimeRateCreateDTO dto = new PartTimeRateCreateDTO();
        dto.setRateCode("P1");
        dto.setRateName("兼职助理工程师");
        dto.setLevelSegment("INVALID");
        dto.setMonthlySalary(new BigDecimal("3000"));
        dto.setEffectiveDate(LocalDate.of(2026, 1, 1));

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建失败: rateCode + version 重复抛 DUPLICATE_KEY")
    void create_duplicateCode() {
        PartTimeRateCreateDTO dto = new PartTimeRateCreateDTO();
        dto.setRateCode("P5");
        dto.setRateName("兼职高级工程师");
        dto.setLevelSegment("MIDDLE");
        dto.setMonthlySalary(new BigDecimal("6000"));
        dto.setEffectiveDate(LocalDate.of(2026, 1, 1));
        when(partTimeRateMapper.selectOne(any())).thenReturn(new PartTimeRateDO());

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.DUPLICATE_KEY.getCode(), ex.getCode());
        verify(partTimeRateMapper, never()).insert(any(PartTimeRateDO.class));
    }

    // ==================== getById ====================

    @Test
    @DisplayName("getById 成功返回费率")
    void getById_success() {
        PartTimeRateDO rate = new PartTimeRateDO();
        rate.setId("R1");
        rate.setRateCode("P5");
        when(partTimeRateMapper.selectById("R1")).thenReturn(rate);

        PartTimeRateDO result = service.getById("R1");
        assertEquals("P5", result.getRateCode());
    }

    @Test
    @DisplayName("getById 不存在抛 NOT_FOUND")
    void getById_notFound() {
        when(partTimeRateMapper.selectById("X")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.getById("X"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== page ====================

    @Test
    @DisplayName("page 查询返回分页结果")
    void page_filter() {
        PartTimeRateDO rate = new PartTimeRateDO();
        rate.setId("R1");
        Page<PartTimeRateDO> mockPage = new Page<>(1, 10, 1);
        mockPage.setRecords(List.of(rate));
        when(partTimeRateMapper.selectPage(any(), any())).thenReturn(mockPage);

        Page<PartTimeRateDO> result = service.page(1, 10, "P", "MIDDLE", "ACTIVE");

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    // ==================== matchEffective ====================

    @Test
    @DisplayName("matchEffective 按编码+日期匹配生效费率")
    void matchEffective_success() {
        PartTimeRateDO rate = new PartTimeRateDO();
        rate.setRateCode("P5");
        when(partTimeRateMapper.selectEffective(anyString(), any())).thenReturn(rate);

        PartTimeRateDO result = service.matchEffective("P5", LocalDate.of(2026, 3, 1));
        assertNotNull(result);
        assertEquals("P5", result.getRateCode());
    }

    @Test
    @DisplayName("matchEffective rateCode 为空返回 null")
    void matchEffective_emptyCode() {
        assertNull(service.matchEffective("", null));
    }

    // ==================== listEffective ====================

    @Test
    @DisplayName("listEffective 返回生效费率列表")
    void listEffective_success() {
        PartTimeRateDO rate = new PartTimeRateDO();
        rate.setRateCode("P1");
        when(partTimeRateMapper.listEffective(any())).thenReturn(List.of(rate));

        List<PartTimeRateDO> result = service.listEffective(LocalDate.of(2026, 3, 1));
        assertEquals(1, result.size());
    }

    // ==================== update ====================

    @Test
    @DisplayName("更新成功: 重新计算 totalCost")
    void update_success_recalculatesTotalCost() {
        PartTimeRateDO existing = new PartTimeRateDO();
        existing.setId("R1");
        existing.setRateCode("P5");
        existing.setMonthlySalary(new BigDecimal("6000"));
        existing.setCommercialInsurance(new BigDecimal("80"));
        when(partTimeRateMapper.selectById("R1")).thenReturn(existing);
        when(partTimeRateMapper.selectOne(any())).thenReturn(null);

        PartTimeRateUpdateDTO dto = new PartTimeRateUpdateDTO();
        dto.setMonthlySalary(new BigDecimal("7000"));
        dto.setCommercialInsurance(new BigDecimal("100"));

        service.update("R1", dto);

        ArgumentCaptor<PartTimeRateDO> captor = ArgumentCaptor.forClass(PartTimeRateDO.class);
        verify(partTimeRateMapper).updateById(captor.capture());
        assertEquals(new BigDecimal("7100.00"), captor.getValue().getTotalCost());
    }

    @Test
    @DisplayName("更新失败: 不存在抛 NOT_FOUND")
    void update_notFound() {
        when(partTimeRateMapper.selectById("X")).thenReturn(null);

        PartTimeRateUpdateDTO dto = new PartTimeRateUpdateDTO();
        dto.setMonthlySalary(new BigDecimal("7000"));

        BizException ex = assertThrows(BizException.class, () -> service.update("X", dto));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== delete ====================

    @Test
    @DisplayName("删除成功")
    void delete_success() {
        PartTimeRateDO existing = new PartTimeRateDO();
        existing.setId("R1");
        when(partTimeRateMapper.selectById("R1")).thenReturn(existing);

        service.delete("R1");

        verify(partTimeRateMapper).deleteById("R1");
    }

    @Test
    @DisplayName("删除失败: 不存在抛 NOT_FOUND")
    void delete_notFound() {
        when(partTimeRateMapper.selectById("X")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.delete("X"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }
}
