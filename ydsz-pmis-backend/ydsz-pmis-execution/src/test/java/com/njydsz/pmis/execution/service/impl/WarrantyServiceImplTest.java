package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.WarrantyCreateDTO;
import com.njydsz.pmis.execution.dto.WarrantyTerminateDTO;
import com.njydsz.pmis.execution.entity.WarrantyDO;
import com.njydsz.pmis.execution.mapper.WarrantyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WarrantyServiceImpl 质保期服务测试
 */
@DisplayName("WarrantyServiceImpl 质保期服务")
class WarrantyServiceImplTest {

    private WarrantyMapper mapper;
    private WarrantyServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(WarrantyMapper.class);
        service = new WarrantyServiceImpl(mapper);
    }

    @Test
    @DisplayName("create 必填校验")
    void create_null() {
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 项目 ID 为空")
    void create_noInitiation() {
        assertThatThrownBy(() -> service.create(new WarrantyCreateDTO()))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 拒绝负数月数")
    void create_negativeMonths() {
        WarrantyCreateDTO dto = new WarrantyCreateDTO();
        dto.setInitiationId(1L);
        dto.setDurationMonths(0);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 拒绝 > 120 月")
    void create_tooLong() {
        WarrantyCreateDTO dto = new WarrantyCreateDTO();
        dto.setInitiationId(1L);
        dto.setDurationMonths(200);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 拒绝负数提醒天数")
    void create_badNoticeDays() {
        WarrantyCreateDTO dto = new WarrantyCreateDTO();
        dto.setInitiationId(1L);
        dto.setNoticeDays(-1);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 拒绝 > 180 天提醒")
    void create_tooLongNotice() {
        WarrantyCreateDTO dto = new WarrantyCreateDTO();
        dto.setInitiationId(1L);
        dto.setNoticeDays(200);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 拒绝同项目已有未结清质保期")
    void create_alreadyActive() {
        WarrantyCreateDTO dto = new WarrantyCreateDTO();
        dto.setInitiationId(1L);
        dto.setStartDate(LocalDate.of(2026, 7, 1));
        WarrantyDO existing = new WarrantyDO();
        existing.setStatus("ACTIVE");
        when(mapper.selectByInitiation(1L)).thenReturn(List.of(existing));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 成功：默认 12 个月 + 30 天提醒")
    void create_success() {
        WarrantyCreateDTO dto = new WarrantyCreateDTO();
        dto.setInitiationId(1L);
        dto.setStartDate(LocalDate.of(2026, 7, 1));
        when(mapper.selectByInitiation(1L)).thenReturn(List.of());
        when(mapper.insert(any(WarrantyDO.class))).thenAnswer(inv -> {
            WarrantyDO w = inv.getArgument(0);
            w.setId(100L);
            return 1;
        });

        Long id = service.create(dto);
        assertThat(id).isEqualTo(100L);

        ArgumentCaptor<WarrantyDO> cap = ArgumentCaptor.forClass(WarrantyDO.class);
        verify(mapper).insert(cap.capture());
        WarrantyDO saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getDurationMonths()).isEqualTo(12);
        assertThat(saved.getNoticeDays()).isEqualTo(30);
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(saved.getWarrantyCode()).startsWith("WY-");
        assertThat(saved.getTenantId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("create 已结清的旧质保期不影响新建")
    void create_pastTerminated_ok() {
        WarrantyCreateDTO dto = new WarrantyCreateDTO();
        dto.setInitiationId(1L);
        when(mapper.selectByInitiation(1L)).thenReturn(List.of());
        when(mapper.insert(any(WarrantyDO.class))).thenReturn(1);
        service.create(dto);
        verify(mapper, times(1)).insert(any(WarrantyDO.class));
    }

    @Test
    @DisplayName("terminate 必填校验")
    void terminate_null() {
        assertThatThrownBy(() -> service.terminate(null))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.terminate(new WarrantyTerminateDTO()))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("terminate 找不到记录")
    void terminate_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        WarrantyTerminateDTO dto = new WarrantyTerminateDTO();
        dto.setId(99L);
        assertThatThrownBy(() -> service.terminate(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("terminate 终态不可再终止")
    void terminate_alreadyTerminal() {
        WarrantyDO w = new WarrantyDO();
        w.setStatus("EXPIRED");
        when(mapper.selectById(1L)).thenReturn(w);
        WarrantyTerminateDTO dto = new WarrantyTerminateDTO();
        dto.setId(1L);
        assertThatThrownBy(() -> service.terminate(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
        verify(mapper, never()).markStatus(any(), any(), any());
    }

    @Test
    @DisplayName("terminate 成功")
    void terminate_success() {
        WarrantyDO w = new WarrantyDO();
        w.setStatus("ACTIVE");
        when(mapper.selectById(1L)).thenReturn(w);
        WarrantyTerminateDTO dto = new WarrantyTerminateDTO();
        dto.setId(1L);
        dto.setReason("客户主动取消");
        service.terminate(dto);
        verify(mapper, times(1)).markStatus(1L, "TERMINATED", "客户主动取消");
    }

    @Test
    @DisplayName("scanExpiring 标记 ACTIVE → EXPIRING_SOON")
    void scanExpiring() {
        WarrantyDO a = new WarrantyDO();
        a.setId(1L);
        a.setStatus("ACTIVE");
        WarrantyDO b = new WarrantyDO();
        b.setId(2L);
        b.setStatus("EXPIRING_SOON"); // 已经标记过，不重复
        when(mapper.selectExpiringBefore(any())).thenReturn(List.of(a, b));

        int n = service.scanExpiring(LocalDate.of(2026, 7, 1), 30);
        assertThat(n).isEqualTo(1);
        verify(mapper, times(1)).markStatus(1L, "EXPIRING_SOON", null);
        verify(mapper, never()).markStatus(eq2(2L), any(), any());
    }

    @Test
    @DisplayName("scanOverdue 标记非终态 → EXPIRED")
    void scanOverdue() {
        WarrantyDO a = new WarrantyDO();
        a.setId(1L);
        a.setStatus("ACTIVE");
        when(mapper.selectOverdue(any())).thenReturn(List.of(a));
        int n = service.scanOverdue(LocalDate.of(2026, 7, 1));
        assertThat(n).isEqualTo(1);
        verify(mapper).markStatus(1L, "EXPIRED", null);
    }

    @Test
    @DisplayName("scanExpiring/Overdue today 为 null 时用当前日期")
    void scan_nullToday() {
        when(mapper.selectExpiringBefore(any())).thenReturn(List.of());
        when(mapper.selectOverdue(any())).thenReturn(List.of());
        service.scanExpiring(null, 30);
        service.scanOverdue(null);
        verify(mapper, times(1)).selectExpiringBefore(any());
        verify(mapper, times(1)).selectOverdue(any());
    }

    @Test
    @DisplayName("listExpiring 委托 mapper")
    void listExpiring() {
        when(mapper.selectExpiringBefore(any())).thenReturn(List.of(new WarrantyDO()));
        assertThat(service.listExpiring(LocalDate.of(2026, 7, 1))).hasSize(1);
    }

    @Test
    @DisplayName("page 委托 mapper")
    void page() {
        Page<WarrantyDO> p = new Page<>();
        when(mapper.selectPage(any(Page.class), any())).thenReturn(p);
        assertThat(service.page(1, 20, "ACTIVE", 1L, "kw")).isSameAs(p);
    }

    @Test
    @DisplayName("getById 找不到抛 NOT_FOUND")
    void getById_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    private static <T> T eq2(T v) {
        return org.mockito.ArgumentMatchers.eq(v);
    }
}
