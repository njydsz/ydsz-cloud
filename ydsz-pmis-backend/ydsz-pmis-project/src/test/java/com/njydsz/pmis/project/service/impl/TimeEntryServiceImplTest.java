package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.TimeEntryApprovalDTO;
import com.njydsz.pmis.project.dto.TimeEntryCreateDTO;
import com.njydsz.pmis.project.entity.RateCardDO;
import com.njydsz.pmis.project.entity.TimeEntryDO;
import com.njydsz.pmis.project.mapper.TimeEntryMapper;
import com.njydsz.pmis.project.service.CostAllocationService;
import com.njydsz.pmis.project.service.RateCardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 工时服务实现单元测试
 *
 * <p>重点验证费率卡填充（P2-impl）：
 * <ul>
 *   <li>create() 创建工时时自动匹配费率卡（成功/失败/异常）</li>
 *   <li>approve() 审批通过时按实际费率归集成本，旧数据无 rate 时兜底 800</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("工时服务实现测试 - 费率卡填充")
class TimeEntryServiceImplTest {

    @Mock
    private TimeEntryMapper timeEntryMapper;
    @Mock
    private NameAssembler nameAssembler;
    @Mock
    private CostAllocationService costAllocationService;
    @Mock
    private RateCardService rateCardService;

    @InjectMocks
    private TimeEntryServiceImpl timeEntryService;

    // =========================================================================
    // create - 费率卡自动匹配
    // =========================================================================

    @Test
    @DisplayName("create - 费率匹配成功应填入 rateId 和 rate")
    void create_费率匹配成功_应填入rateId和Rate() {
        // Arrange
        TimeEntryCreateDTO dto = buildValidDTO();
        RateCardDO card = new RateCardDO();
        card.setId(100L);
        card.setRateAmount(new BigDecimal("1200"));
        when(rateCardService.matchEffective(eq("L8"), isNull(), isNull(), eq(LocalDate.of(2026, 3, 10))))
                .thenReturn(card);

        // Act
        timeEntryService.create(dto);

        // Assert - 捕获 insert 的实体，验证费率已填入
        ArgumentCaptor<TimeEntryDO> captor = ArgumentCaptor.forClass(TimeEntryDO.class);
        verify(timeEntryMapper).insert(captor.capture());
        TimeEntryDO saved = captor.getValue();
        assertThat(saved.getRateId()).isEqualTo(100L);
        assertThat(saved.getRate()).isEqualByComparingTo("1200");
    }

    @Test
    @DisplayName("create - 费率匹配失败（返回 null）不阻断创建，rate 为 null")
    void create_费率匹配失败_不阻断创建且Rate为Null() {
        // Arrange
        TimeEntryCreateDTO dto = buildValidDTO();
        when(rateCardService.matchEffective(eq("L8"), isNull(), isNull(), eq(LocalDate.of(2026, 3, 10))))
                .thenReturn(null);

        // Act
        timeEntryService.create(dto);

        // Assert - 创建未被阻断，rate 留空
        ArgumentCaptor<TimeEntryDO> captor = ArgumentCaptor.forClass(TimeEntryDO.class);
        verify(timeEntryMapper).insert(captor.capture());
        TimeEntryDO saved = captor.getValue();
        assertThat(saved.getRateId()).isNull();
        assertThat(saved.getRate()).isNull();
    }

    @Test
    @DisplayName("create - 费率匹配异常不阻断创建，rate 为 null")
    void create_费率匹配异常_不阻断创建且Rate为Null() {
        // Arrange
        TimeEntryCreateDTO dto = buildValidDTO();
        when(rateCardService.matchEffective(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("费率服务不可用"));

        // Act
        timeEntryService.create(dto);

        // Assert - 异常被吞掉，创建未被阻断
        ArgumentCaptor<TimeEntryDO> captor = ArgumentCaptor.forClass(TimeEntryDO.class);
        verify(timeEntryMapper).insert(captor.capture());
        TimeEntryDO saved = captor.getValue();
        assertThat(saved.getRateId()).isNull();
        assertThat(saved.getRate()).isNull();
    }

    @Test
    @DisplayName("create - 用户已指定 rateId 时不触发自动匹配")
    void create_用户已指定RateId_不触发自动匹配() {
        // Arrange
        TimeEntryCreateDTO dto = buildValidDTO();
        dto.setRateId(999L);
        dto.setRate(new BigDecimal("2000"));

        // Act
        timeEntryService.create(dto);

        // Assert - 不调用费率匹配，沿用用户指定的费率
        verify(rateCardService, never()).matchEffective(any(), any(), any(), any());
        ArgumentCaptor<TimeEntryDO> captor = ArgumentCaptor.forClass(TimeEntryDO.class);
        verify(timeEntryMapper).insert(captor.capture());
        TimeEntryDO saved = captor.getValue();
        assertThat(saved.getRateId()).isEqualTo(999L);
        assertThat(saved.getRate()).isEqualByComparingTo("2000");
    }

    // =========================================================================
    // approve - 成本归集费率核算
    // =========================================================================

    @Test
    @DisplayName("approve - 有 rate 时按实际费率归集成本")
    void approve_有Rate_按实际费率归集成本() {
        // Arrange
        TimeEntryDO entry = buildSubmittedEntry();
        entry.setRate(new BigDecimal("1500"));
        entry.setRateId(200L);
        entry.setDays(new BigDecimal("1.0"));
        when(timeEntryMapper.selectById(1L)).thenReturn(entry);

        TimeEntryApprovalDTO dto = new TimeEntryApprovalDTO();
        dto.setId(1L);
        dto.setTargetStatus("APPROVED");
        dto.setApproverId(10L);
        dto.setApproverName("审批人");

        // Act
        timeEntryService.approve(dto);

        // Assert - 成本 = 1 人天 × 1500 元 = 1500 元
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(costAllocationService).syncFromTimeEntry(
                eq(1L), eq(100L), eq(1000L), eq("张三"),
                eq("L8"), eq("2026-03"), amountCaptor.capture(), eq(true));
        assertThat(amountCaptor.getValue()).isEqualByComparingTo("1500");
    }

    @Test
    @DisplayName("approve - 无 rate 时兜底 800 元/人天归集成本")
    void approve_无Rate_兜底800归集成本() {
        // Arrange - 旧数据无 rate 字段
        TimeEntryDO entry = buildSubmittedEntry();
        entry.setRate(null);
        entry.setRateId(null);
        entry.setDays(new BigDecimal("2.0"));
        when(timeEntryMapper.selectById(1L)).thenReturn(entry);

        TimeEntryApprovalDTO dto = new TimeEntryApprovalDTO();
        dto.setId(1L);
        dto.setTargetStatus("APPROVED");
        dto.setApproverId(10L);
        dto.setApproverName("审批人");

        // Act
        timeEntryService.approve(dto);

        // Assert - 成本 = 2 人天 × 800 元 = 1600 元（兜底）
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(costAllocationService).syncFromTimeEntry(
                eq(1L), eq(100L), eq(1000L), eq("张三"),
                eq("L8"), eq("2026-03"), amountCaptor.capture(), eq(true));
        assertThat(amountCaptor.getValue()).isEqualByComparingTo("1600");
    }

    // =========================================================================
    // 测试数据构造
    // =========================================================================

    /** 构造合法的工时创建 DTO（含员工姓名，避免触发 nameAssembler） */
    private TimeEntryCreateDTO buildValidDTO() {
        TimeEntryCreateDTO dto = new TimeEntryCreateDTO();
        dto.setEntryDate(LocalDate.of(2026, 3, 10));
        dto.setEmployeeId(1000L);
        dto.setEmployeeName("张三");
        dto.setLevelCode("L8");
        dto.setInitiationId(100L);
        dto.setInitiationName("测试项目");
        dto.setHours(new BigDecimal("8"));
        dto.setOvertime(BigDecimal.ZERO);
        dto.setWorkType("REGULAR");
        dto.setDescription("费率填充测试");
        return dto;
    }

    /** 构造已提交状态的工时实体 */
    private TimeEntryDO buildSubmittedEntry() {
        TimeEntryDO e = new TimeEntryDO();
        e.setId(1L);
        e.setEntryDate(LocalDate.of(2026, 3, 10));
        e.setEmployeeId(1000L);
        e.setEmployeeName("张三");
        e.setLevelCode("L8");
        e.setInitiationId(100L);
        e.setHours(new BigDecimal("8"));
        e.setDays(new BigDecimal("1.0"));
        e.setStatus("SUBMITTED");
        return e;
    }
}
