package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.config.ThresholdProvider;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.entity.EvmMeasureDO;
import com.njydsz.pmis.project.mapper.EvmMeasureMapper;
import com.njydsz.pmis.project.vo.EvmMeasureVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * EVM 挣值测量服务实现单元测试
 *
 * <p>重点验证 P0-D2 DO/VO 分离改造：
 * <ul>
 *   <li>对外接口返回 VO 而非 DO</li>
 *   <li>VO 剥离 tenantId / providerTraceId / deleted 等敏感字段</li>
 *   <li>分页元数据（current/size/total）正确保留</li>
 *   <li>null / 空集合守卫</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EVM 测量服务实现测试 - DO/VO 分离")
class EvmMeasureServiceImplTest {

    @Mock
    private EvmMeasureMapper evmMapper;

    @Mock
    private ThresholdProvider thresholdProvider;

    @InjectMocks
    private EvmMeasureServiceImpl evmService;

    // =========================================================================
    //  getById - DO/VO 转换
    // =========================================================================

    @Test
    @DisplayName("getById - 找到记录应返回 VO 且剥离 tenantId/providerTraceId/deleted")
    void getById_shouldReturnVoWithoutSensitiveFields() {
        // Arrange
        EvmMeasureDO m = buildDO(1L, 100L, "2026-07");
        m.setTenantId(99L);
        m.setProviderTraceId("trace-xxx");
        m.setDeleted(0);
        when(evmMapper.selectById(1L)).thenReturn(m);

        // Act
        EvmMeasureVO v = evmService.getById(1L);

        // Assert - 业务字段保留
        assertNotNull(v);
        assertEquals(1L, v.getId());
        assertEquals(100L, v.getInitiationId());
        assertEquals("2026-07", v.getPeriod());
        assertEquals(new BigDecimal("100.00"), v.getPv());
        assertEquals("YELLOW", v.getAlertLevel());
        // Assert - 敏感字段已剥离：通过反射确认 VO 确实无 tenantId/providerTraceId/deleted 方法
        assertThrows(NoSuchMethodException.class,
                () -> EvmMeasureVO.class.getDeclaredMethod("getTenantId"));
        assertThrows(NoSuchMethodException.class,
                () -> EvmMeasureVO.class.getDeclaredMethod("getProviderTraceId"));
        assertThrows(NoSuchMethodException.class,
                () -> EvmMeasureVO.class.getDeclaredMethod("getDeleted"));
    }

    @Test
    @DisplayName("getById - id 为空应抛 BAD_REQUEST")
    void getById_nullId_shouldThrow() {
        BizException ex = assertThrows(BizException.class, () -> evmService.getById(null));
        assertNotNull(ex);
    }

    @Test
    @DisplayName("getById - 记录不存在应抛 NOT_FOUND")
    void getById_notFound_shouldThrow() {
        when(evmMapper.selectById(404L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> evmService.getById(404L));
        assertNotNull(ex);
    }

    // =========================================================================
    //  listByInitiation / listByWbs - 列表转换
    // =========================================================================

    @Test
    @DisplayName("listByInitiation - null 守卫返回空列表")
    void listByInitiation_null_shouldReturnEmpty() {
        assertEquals(List.of(), evmService.listByInitiation(null));
        verifyNoInteractions(evmMapper);
    }

    @Test
    @DisplayName("listByInitiation - 空集合守卫不调用 toVo")
    void listByInitiation_emptyList_shouldReturnEmpty() {
        when(evmMapper.selectByInitiation(100L)).thenReturn(List.of());
        assertEquals(List.of(), evmService.listByInitiation(100L));
    }

    @Test
    @DisplayName("listByInitiation - 多条记录应逐条转换为 VO")
    void listByInitiation_normal_shouldConvertAll() {
        EvmMeasureDO m1 = buildDO(1L, 100L, "2026-07");
        EvmMeasureDO m2 = buildDO(2L, 100L, "2026-08");
        when(evmMapper.selectByInitiation(100L)).thenReturn(List.of(m1, m2));

        List<EvmMeasureVO> result = evmService.listByInitiation(100L);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        assertEquals("2026-07", result.get(0).getPeriod());
        assertEquals("2026-08", result.get(1).getPeriod());
    }

    @Test
    @DisplayName("listByWbs - null 守卫返回空列表")
    void listByWbs_null_shouldReturnEmpty() {
        assertEquals(List.of(), evmService.listByWbs(null));
        verifyNoInteractions(evmMapper);
    }

    @Test
    @DisplayName("listByWbs - 正常转换")
    void listByWbs_normal_shouldConvert() {
        EvmMeasureDO m = buildDO(1L, 100L, "2026-07");
        when(evmMapper.selectByWbs(50L)).thenReturn(List.of(m));

        List<EvmMeasureVO> result = evmService.listByWbs(50L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    // =========================================================================
    //  page - 分页元数据保留 + 记录转换
    // =========================================================================

    @Test
    @DisplayName("page - 应保留分页元数据并转换为 VO 列表")
    void page_shouldKeepPageMetadataAndConvertRecords() {
        // Arrange
        EvmMeasureDO m1 = buildDO(1L, 100L, "2026-07");
        EvmMeasureDO m2 = buildDO(2L, 100L, "2026-08");
        Page<EvmMeasureDO> doPage = new Page<>(1, 10, 2);
        doPage.setRecords(List.of(m1, m2));
        when(evmMapper.selectPage(any(Page.class), any())).thenReturn(doPage);

        // Act
        Page<EvmMeasureVO> voPage = evmService.page(1, 10, 100L, null);

        // Assert - 分页元数据保留
        assertEquals(1L, voPage.getCurrent());
        assertEquals(10L, voPage.getSize());
        assertEquals(2L, voPage.getTotal());
        // Assert - 记录已转换为 VO
        assertNotNull(voPage.getRecords());
        assertEquals(2, voPage.getRecords().size());
        assertInstanceOf(EvmMeasureVO.class, voPage.getRecords().get(0));
        assertEquals(1L, voPage.getRecords().get(0).getId());
    }

    @Test
    @DisplayName("page - 空结果集应返回空 records 而非 null")
    void page_emptyResult_shouldReturnEmptyRecords() {
        Page<EvmMeasureDO> doPage = new Page<>(1, 10, 0);
        doPage.setRecords(List.of());
        when(evmMapper.selectPage(any(Page.class), any())).thenReturn(doPage);

        Page<EvmMeasureVO> voPage = evmService.page(1, 10, null, null);

        assertNotNull(voPage.getRecords());
        assertTrue(voPage.getRecords().isEmpty());
        assertEquals(0L, voPage.getTotal());
    }

    // =========================================================================
    //  辅助方法
    // =========================================================================

    private EvmMeasureDO buildDO(Long id, Long initiationId, String period) {
        EvmMeasureDO m = new EvmMeasureDO();
        m.setId(id);
        m.setInitiationId(initiationId);
        m.setPeriod(period);
        m.setPv(new BigDecimal("100.00"));
        m.setEv(new BigDecimal("90.00"));
        m.setAc(new BigDecimal("95.00"));
        m.setBac(new BigDecimal("1000.00"));
        m.setCpi(new BigDecimal("0.95"));
        m.setSpi(new BigDecimal("0.90"));
        m.setAlertLevel("YELLOW");
        m.setAlertReason("成本超支预警");
        m.setMeasureDate(LocalDate.of(2026, 7, 31));
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        return m;
    }
}
