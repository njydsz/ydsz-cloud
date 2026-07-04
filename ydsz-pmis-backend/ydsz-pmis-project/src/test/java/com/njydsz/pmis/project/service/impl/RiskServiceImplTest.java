package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.RiskStatusDTO;
import com.njydsz.pmis.project.entity.RiskDO;
import com.njydsz.pmis.project.mapper.RiskMapper;
import com.njydsz.pmis.project.vo.RiskVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 项目风险服务实现单元测试
 *
 * <p>重点验证 P0-D2 DO/VO 分离改造：
 * <ul>
 *   <li>对外接口返回 VO 而非 DO</li>
 *   <li>VO 剥离 tenantId / providerTraceId / deleted / version 等敏感字段</li>
 *   <li>内部方法 changeStatus / delete 仍使用 DO（loadByIdDO），不受 VO 改造影响</li>
 *   <li>状态迁移合法性校验</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("风险服务实现测试 - DO/VO 分离")
class RiskServiceImplTest {

    @Mock
    private RiskMapper riskMapper;

    @InjectMocks
    private RiskServiceImpl riskService;

    // =========================================================================
    //  getById - DO/VO 转换
    // =========================================================================

    @Test
    @DisplayName("getById - 找到记录应返回 VO 且剥离 tenantId/providerTraceId/deleted/version")
    void getById_shouldReturnVoWithoutSensitiveFields() {
        // Arrange
        RiskDO r = buildDO(1L, "R-001");
        r.setTenantId(99L);
        r.setProviderTraceId("trace-xxx");
        r.setDeleted(0);
        r.setVersion(3);
        when(riskMapper.selectById(1L)).thenReturn(r);

        // Act
        RiskVO v = riskService.getById(1L);

        // Assert - 业务字段保留
        assertNotNull(v);
        assertEquals(1L, v.getId());
        assertEquals("R-001", v.getRiskCode());
        assertEquals("OPEN", v.getStatus());
        assertEquals("HIGH", v.getRiskLevel());
        // Assert - 敏感字段已剥离：通过反射确认 VO 无对应 getter
        assertThrows(NoSuchMethodException.class,
                () -> RiskVO.class.getDeclaredMethod("getTenantId"));
        assertThrows(NoSuchMethodException.class,
                () -> RiskVO.class.getDeclaredMethod("getProviderTraceId"));
        assertThrows(NoSuchMethodException.class,
                () -> RiskVO.class.getDeclaredMethod("getDeleted"));
        assertThrows(NoSuchMethodException.class,
                () -> RiskVO.class.getDeclaredMethod("getVersion"));
    }

    @Test
    @DisplayName("getById - 记录不存在应抛 NOT_FOUND")
    void getById_notFound_shouldThrow() {
        when(riskMapper.selectById(404L)).thenReturn(null);
        assertThrows(BizException.class, () -> riskService.getById(404L));
    }

    // =========================================================================
    //  page / listByInitiation
    // =========================================================================

    @Test
    @DisplayName("page - 应保留分页元数据并转换为 VO 列表")
    void page_shouldKeepPageMetadataAndConvertRecords() {
        RiskDO r1 = buildDO(1L, "R-001");
        RiskDO r2 = buildDO(2L, "R-002");
        Page<RiskDO> doPage = new Page<>(1, 10, 2);
        doPage.setRecords(List.of(r1, r2));
        when(riskMapper.selectPage(any(Page.class), any())).thenReturn(doPage);

        Page<RiskVO> voPage = riskService.page(1, 10, null, null, null, null);

        assertEquals(1L, voPage.getCurrent());
        assertEquals(10L, voPage.getSize());
        assertEquals(2L, voPage.getTotal());
        assertNotNull(voPage.getRecords());
        assertEquals(2, voPage.getRecords().size());
        assertInstanceOf(RiskVO.class, voPage.getRecords().get(0));
    }

    @Test
    @DisplayName("page - 空结果集应返回空 records 而非 null")
    void page_emptyResult_shouldReturnEmptyRecords() {
        Page<RiskDO> doPage = new Page<>(1, 10, 0);
        doPage.setRecords(List.of());
        when(riskMapper.selectPage(any(Page.class), any())).thenReturn(doPage);

        Page<RiskVO> voPage = riskService.page(1, 10, null, null, null, null);

        assertNotNull(voPage.getRecords());
        assertTrue(voPage.getRecords().isEmpty());
    }

    @Test
    @DisplayName("listByInitiation - null 守卫返回空列表")
    void listByInitiation_null_shouldReturnEmpty() {
        assertEquals(List.of(), riskService.listByInitiation(null));
        verifyNoInteractions(riskMapper);
    }

    @Test
    @DisplayName("listByInitiation - 正常转换")
    void listByInitiation_normal_shouldConvert() {
        RiskDO r = buildDO(1L, "R-001");
        when(riskMapper.selectByInitiation(100L)).thenReturn(List.of(r));

        List<RiskVO> result = riskService.listByInitiation(100L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    // =========================================================================
    //  changeStatus - 内部仍使用 DO（loadByIdDO）
    // =========================================================================

    @Test
    @DisplayName("changeStatus - 内部应通过 loadByIdDO 加载 DO，而非返回 VO 的 getById")
    void changeStatus_shouldUseLoadByIdDO() {
        // Arrange
        RiskDO r = buildDO(1L, "R-001");
        r.setStatus("OPEN");
        when(riskMapper.selectById(1L)).thenReturn(r);
        when(riskMapper.updateStatus(eq(1L), eq("MITIGATING"))).thenReturn(1);
        when(riskMapper.updateById(any(RiskDO.class))).thenReturn(1);

        RiskStatusDTO dto = new RiskStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("MITIGATING");

        // Act
        riskService.changeStatus(dto);

        // Assert - 状态迁移成功，未抛异常
        verify(riskMapper, times(1)).updateStatus(1L, "MITIGATING");
        verify(riskMapper, times(1)).updateById(any(RiskDO.class));
    }

    @Test
    @DisplayName("changeStatus - 非法状态迁移应抛 BizException")
    void changeStatus_illegalTransition_shouldThrow() {
        // Arrange: CLOSED 不可再迁移
        RiskDO r = buildDO(1L, "R-001");
        r.setStatus("CLOSED");
        when(riskMapper.selectById(1L)).thenReturn(r);

        RiskStatusDTO dto = new RiskStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("MITIGATING");

        // Act + Assert
        assertThrows(BizException.class, () -> riskService.changeStatus(dto));
        verify(riskMapper, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    @DisplayName("delete - OCCURRED 状态不可删除")
    void delete_occurredStatus_shouldThrow() {
        RiskDO r = buildDO(1L, "R-001");
        r.setStatus("OCCURRED");
        when(riskMapper.selectById(1L)).thenReturn(r);

        assertThrows(BizException.class, () -> riskService.delete(1L));
        verify(riskMapper, never()).deleteById(anyLong());
    }

    // =========================================================================
    //  辅助方法
    // =========================================================================

    private RiskDO buildDO(Long id, String riskCode) {
        RiskDO r = new RiskDO();
        r.setId(id);
        r.setRiskCode(riskCode);
        r.setInitiationId(100L);
        r.setRiskTitle("测试风险");
        r.setRiskType("SCHEDULE");
        r.setProbability("HIGH");
        r.setImpact("HIGH");
        r.setRiskLevel("HIGH");
        r.setStatus("OPEN");
        r.setOwnerId(20L);
        r.setOwnerName("张三");
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        return r;
    }
}
