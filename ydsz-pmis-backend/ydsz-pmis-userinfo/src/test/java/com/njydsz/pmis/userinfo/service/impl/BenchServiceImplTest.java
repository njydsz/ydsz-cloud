package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.userinfo.dto.BenchRecordCreateDTO;
import com.njydsz.pmis.userinfo.entity.BenchRecordDO;
import com.njydsz.pmis.userinfo.mapper.BenchRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Bench闲置池服务测试")
class BenchServiceImplTest {

    @Mock
    private BenchRecordMapper benchMapper;

    @InjectMocks
    private BenchServiceImpl benchService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("ENTER动作创建Bench记录")
    void act_enter_shouldCreateBenchRecord() {
        BenchRecordCreateDTO dto = new BenchRecordCreateDTO();
        dto.setBenchCode("BENCH001");
        dto.setEmployeeId(1L);
        dto.setAction("ENTER");
        dto.setBenchDate(LocalDate.now());

        when(benchMapper.selectByCode("BENCH001")).thenReturn(null);
        when(benchMapper.selectActiveByEmployee(1L)).thenReturn(null);
        doAnswer(invocation -> {
            BenchRecordDO entity = invocation.getArgument(0);
            entity.setId(500L);
            return 1;
        }).when(benchMapper).insert(any(BenchRecordDO.class));

        Long id = benchService.act(dto);
        assertNotNull(id);
        assertEquals(500L, id);
        verify(benchMapper).insert(any(BenchRecordDO.class));
    }

    @Test
    @DisplayName("ENTER动作时员工已有活跃Bench记录抛出异常")
    void act_enter_duplicateActive_shouldThrowException() {
        BenchRecordCreateDTO dto = new BenchRecordCreateDTO();
        dto.setBenchCode("BENCH002");
        dto.setEmployeeId(1L);
        dto.setAction("ENTER");
        dto.setBenchDate(LocalDate.now());

        BenchRecordDO active = new BenchRecordDO();
        active.setId(1L);
        active.setBenchCode("BENCH001");

        when(benchMapper.selectByCode("BENCH002")).thenReturn(null);
        when(benchMapper.selectActiveByEmployee(1L)).thenReturn(active);

        BizException ex = assertThrows(BizException.class, () -> benchService.act(dto));
        assertEquals(10001, ex.getCode());
    }

    @Test
    @DisplayName("根据ID查询Bench记录")
    void getById_shouldReturnBenchRecord() {
        BenchRecordDO record = new BenchRecordDO();
        record.setId(1L);
        record.setBenchCode("BENCH001");
        record.setEmployeeId(1L);
        record.setStatus("ACTIVE");

        when(benchMapper.selectById(1L)).thenReturn(record);

        BenchRecordDO result = benchService.getById(1L);
        assertNotNull(result);
        assertEquals("BENCH001", result.getBenchCode());
    }

    @Test
    @DisplayName("根据ID查询不存在的Bench记录时抛出异常")
    void getById_notFound_shouldThrowException() {
        when(benchMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> benchService.getById(999L));
        assertEquals(10101, ex.getCode());
    }

    @Test
    @DisplayName("分页查询Bench记录")
    void page_shouldReturnPagedResult() {
        Page<BenchRecordDO> mockPage = new Page<>(1, 10);
        BenchRecordDO record = new BenchRecordDO();
        record.setId(1L);
        record.setBenchCode("BENCH001");
        mockPage.setRecords(List.of(record));
        mockPage.setTotal(1);

        when(benchMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<BenchRecordDO> result = benchService.page(1, 10, null, null);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("计算总闲置成本")
    void totalIdleCost_shouldReturnTotalCost() {
        when(benchMapper.aggregateByPool(anyString())).thenReturn(List.of());

        BigDecimal result = benchService.totalIdleCost();
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result);
    }
}