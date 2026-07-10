package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.rate.OutsourceRateCreateDTO;
import com.njydsz.pmis.userinfo.dto.rate.OutsourceRateUpdateDTO;
import com.njydsz.pmis.userinfo.entity.rate.OutsourceRateDO;
import com.njydsz.pmis.userinfo.mapper.rate.OutsourceRateMapper;
import com.njydsz.pmis.userinfo.service.impl.rate.OutsourceRateServiceImpl;
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
 * OutsourceRateServiceImpl 单元测试
 *
 * <p>覆盖外包职级费率 CRUD 核心行为：人天核算月薪(monthlySalary=dailyRate×monthlyDays)+差旅报销+差旅补贴自动计算 totalCost、
 * 级别编码+版本唯一性校验、级别段位校验、日期校验、生效费率匹配。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutsourceRateServiceImpl 外包职级费率服务测试")
class OutsourceRateServiceImplTest {

    @Mock
    private OutsourceRateMapper outsourceRateMapper;

    @InjectMocks
    private OutsourceRateServiceImpl service;

    // ==================== create ====================

    @Test
    @DisplayName("创建成功: 自动计算 monthlySalary=dailyRate×monthlyDays, totalCost=monthlySalary+travelReimbursement+travelAllowance")
    void create_success_calculatesTotalCost() {
        OutsourceRateCreateDTO dto = new OutsourceRateCreateDTO();
        dto.setRateCode("V5");
        dto.setRateName("外包高级工程师");
        dto.setLevelSegment("MIDDLE");
        dto.setDailyRate(new BigDecimal("227.27"));
        dto.setMonthlyDays(new BigDecimal("22"));
        dto.setTravelReimbursement(new BigDecimal("500"));
        dto.setTravelAllowance(new BigDecimal("300"));
        dto.setEffectiveDate(LocalDate.of(2026, 1, 1));
        when(outsourceRateMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            inv.<OutsourceRateDO>getArgument(0).setId("OR-GENERATED");
            return 1;
        }).when(outsourceRateMapper).insert(any(OutsourceRateDO.class));

        String id = service.create(dto);

        ArgumentCaptor<OutsourceRateDO> captor = ArgumentCaptor.forClass(OutsourceRateDO.class);
        verify(outsourceRateMapper).insert(captor.capture());
        OutsourceRateDO saved = captor.getValue();
        assertEquals("V5", saved.getRateCode());
        // monthlySalary = 227.27 × 22 = 4999.94
        assertEquals(new BigDecimal("4999.94"), saved.getMonthlySalary());
        // totalCost = 4999.94 + 500 + 300 = 5799.94
        assertEquals(new BigDecimal("5799.94"), saved.getTotalCost());
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals(1, saved.getVersion());
        assertEquals("OR-GENERATED", id);
    }

    @Test
    @DisplayName("创建成功: travelReimbursement/travelAllowance 为 null 时 totalCost = monthlySalary")
    void create_success_nullTravel() {
        OutsourceRateCreateDTO dto = new OutsourceRateCreateDTO();
        dto.setRateCode("V1");
        dto.setRateName("外包助理工程师");
        dto.setLevelSegment("PRIMARY");
        dto.setDailyRate(new BigDecimal("113.64"));
        dto.setMonthlyDays(new BigDecimal("22"));
        dto.setEffectiveDate(LocalDate.of(2026, 1, 1));
        when(outsourceRateMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            inv.<OutsourceRateDO>getArgument(0).setId("OR-2");
            return 1;
        }).when(outsourceRateMapper).insert(any(OutsourceRateDO.class));

        service.create(dto);

        ArgumentCaptor<OutsourceRateDO> captor = ArgumentCaptor.forClass(OutsourceRateDO.class);
        verify(outsourceRateMapper).insert(captor.capture());
        // monthlySalary = 113.64 × 22 = 2500.08
        assertEquals(new BigDecimal("2500.08"), captor.getValue().getTotalCost());
    }

    @Test
    @DisplayName("创建失败: dailyRate 不为正数抛 BAD_REQUEST")
    void create_invalidSalary() {
        OutsourceRateCreateDTO dto = new OutsourceRateCreateDTO();
        dto.setRateCode("V1");
        dto.setRateName("外包助理工程师");
        dto.setLevelSegment("PRIMARY");
        dto.setDailyRate(BigDecimal.ZERO);
        dto.setEffectiveDate(LocalDate.of(2026, 1, 1));

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(outsourceRateMapper, never()).insert(any(OutsourceRateDO.class));
    }

    @Test
    @DisplayName("创建失败: 级别段位非法抛 BAD_REQUEST")
    void create_invalidSegment() {
        OutsourceRateCreateDTO dto = new OutsourceRateCreateDTO();
        dto.setRateCode("V1");
        dto.setRateName("外包助理工程师");
        dto.setLevelSegment("INVALID");
        dto.setDailyRate(new BigDecimal("113.64"));
        dto.setEffectiveDate(LocalDate.of(2026, 1, 1));

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建失败: rateCode + version 重复抛 DUPLICATE_KEY")
    void create_duplicateCode() {
        OutsourceRateCreateDTO dto = new OutsourceRateCreateDTO();
        dto.setRateCode("V5");
        dto.setRateName("外包高级工程师");
        dto.setLevelSegment("MIDDLE");
        dto.setDailyRate(new BigDecimal("227.27"));
        dto.setEffectiveDate(LocalDate.of(2026, 1, 1));
        when(outsourceRateMapper.selectOne(any())).thenReturn(new OutsourceRateDO());

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.DUPLICATE_KEY.getCode(), ex.getCode());
        verify(outsourceRateMapper, never()).insert(any(OutsourceRateDO.class));
    }

    // ==================== getById ====================

    @Test
    @DisplayName("getById 成功返回费率")
    void getById_success() {
        OutsourceRateDO rate = new OutsourceRateDO();
        rate.setId("R1");
        rate.setRateCode("V5");
        when(outsourceRateMapper.selectById("R1")).thenReturn(rate);

        OutsourceRateDO result = service.getById("R1");
        assertEquals("V5", result.getRateCode());
    }

    @Test
    @DisplayName("getById 不存在抛 NOT_FOUND")
    void getById_notFound() {
        when(outsourceRateMapper.selectById("X")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.getById("X"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== page ====================

    @Test
    @DisplayName("page 查询返回分页结果")
    void page_filter() {
        OutsourceRateDO rate = new OutsourceRateDO();
        rate.setId("R1");
        Page<OutsourceRateDO> mockPage = new Page<>(1, 10, 1);
        mockPage.setRecords(List.of(rate));
        when(outsourceRateMapper.selectPage(any(), any())).thenReturn(mockPage);

        Page<OutsourceRateDO> result = service.page(1, 10, "V", "MIDDLE", "ACTIVE");

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    // ==================== matchEffective ====================

    @Test
    @DisplayName("matchEffective 按编码+日期匹配生效费率")
    void matchEffective_success() {
        OutsourceRateDO rate = new OutsourceRateDO();
        rate.setRateCode("V5");
        when(outsourceRateMapper.selectEffective(anyString(), any())).thenReturn(rate);

        OutsourceRateDO result = service.matchEffective("V5", LocalDate.of(2026, 3, 1));
        assertNotNull(result);
        assertEquals("V5", result.getRateCode());
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
        OutsourceRateDO rate = new OutsourceRateDO();
        rate.setRateCode("V1");
        when(outsourceRateMapper.listEffective(any())).thenReturn(List.of(rate));

        List<OutsourceRateDO> result = service.listEffective(LocalDate.of(2026, 3, 1));
        assertEquals(1, result.size());
    }

    // ==================== update ====================

    @Test
    @DisplayName("更新成功: 修改人天单价后重新推导月薪和 totalCost")
    void update_success_recalculatesTotalCost() {
        OutsourceRateDO existing = new OutsourceRateDO();
        existing.setId("R1");
        existing.setRateCode("V5");
        existing.setDailyRate(new BigDecimal("227.27"));
        existing.setMonthlyDays(new BigDecimal("22"));
        existing.setMonthlySalary(new BigDecimal("4999.94"));
        existing.setTravelReimbursement(new BigDecimal("500"));
        existing.setTravelAllowance(new BigDecimal("300"));
        when(outsourceRateMapper.selectById("R1")).thenReturn(existing);
        when(outsourceRateMapper.selectOne(any())).thenReturn(null);

        OutsourceRateUpdateDTO dto = new OutsourceRateUpdateDTO();
        dto.setDailyRate(new BigDecimal("272.73"));
        dto.setTravelReimbursement(new BigDecimal("800"));
        dto.setTravelAllowance(new BigDecimal("500"));

        service.update("R1", dto);

        ArgumentCaptor<OutsourceRateDO> captor = ArgumentCaptor.forClass(OutsourceRateDO.class);
        verify(outsourceRateMapper).updateById(captor.capture());
        OutsourceRateDO updated = captor.getValue();
        // monthlySalary = 272.73 × 22 = 6000.06
        assertEquals(new BigDecimal("6000.06"), updated.getMonthlySalary());
        // totalCost = 6000.06 + 800 + 500 = 7300.06
        assertEquals(new BigDecimal("7300.06"), updated.getTotalCost());
    }

    @Test
    @DisplayName("更新失败: 不存在抛 NOT_FOUND")
    void update_notFound() {
        when(outsourceRateMapper.selectById("X")).thenReturn(null);

        OutsourceRateUpdateDTO dto = new OutsourceRateUpdateDTO();
        dto.setDailyRate(new BigDecimal("272.73"));

        BizException ex = assertThrows(BizException.class, () -> service.update("X", dto));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== delete ====================

    @Test
    @DisplayName("删除成功")
    void delete_success() {
        OutsourceRateDO existing = new OutsourceRateDO();
        existing.setId("R1");
        when(outsourceRateMapper.selectById("R1")).thenReturn(existing);

        service.delete("R1");

        verify(outsourceRateMapper).deleteById("R1");
    }

    @Test
    @DisplayName("删除失败: 不存在抛 NOT_FOUND")
    void delete_notFound() {
        when(outsourceRateMapper.selectById("X")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.delete("X"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }
}
