package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PartTimeRateServiceImpl 单元测试
 *
 * <p>使用 Mockito + JUnit5，不启动 Spring 上下文。覆盖 create/update/delete/getById/page/
 * matchEffective/listEffective 的成功与失败路径。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PartTimeRateServiceImpl 兼职工时单价服务测试")
class PartTimeRateServiceImplTest {

    @Mock
    private PartTimeRateMapper partTimeRateMapper;

    @InjectMocks
    private PartTimeRateServiceImpl service;

    /**
     * 构造一份合法的创建参数
     *
     * @return 创建 DTO
     */
    private PartTimeRateCreateDTO baseCreateDto() {
        PartTimeRateCreateDTO dto = new PartTimeRateCreateDTO();
        dto.setRateCode("P1");
        dto.setRateName("初级兼职");
        dto.setHourlyRate(new BigDecimal("10.00"));
        dto.setSegment("PRIMARY");
        dto.setEffectiveDate(LocalDate.of(2025, 1, 1));
        return dto;
    }

    // ==================== create ====================

    @Test
    @DisplayName("create 成功: 默认 status=ACTIVE, version=1")
    void create_success() {
        PartTimeRateCreateDTO dto = baseCreateDto();
        when(partTimeRateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(partTimeRateMapper.insert(any(PartTimeRateDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, PartTimeRateDO.class).setId("NEW_ID");
            return 1;
        });

        String id = service.create(dto);

        assertEquals("NEW_ID", id);
        verify(partTimeRateMapper).insert(any(PartTimeRateDO.class));
    }

    @Test
    @DisplayName("create 失败: rateCode 重复抛 DUPLICATE_KEY")
    void create_duplicateRateCode() {
        PartTimeRateCreateDTO dto = baseCreateDto();
        when(partTimeRateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new PartTimeRateDO());

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.DUPLICATE_KEY.getCode(), ex.getCode());
        verify(partTimeRateMapper, never()).insert(any(PartTimeRateDO.class));
    }

    @Test
    @DisplayName("create 失败: hourlyRate <= 0 抛 BAD_REQUEST")
    void create_invalidHourlyRate() {
        PartTimeRateCreateDTO dto = baseCreateDto();
        dto.setHourlyRate(BigDecimal.ZERO);

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(partTimeRateMapper, never()).insert(any(PartTimeRateDO.class));
    }

    @Test
    @DisplayName("create 失败: 失效日期早于生效日期抛 BAD_REQUEST")
    void create_invalidDates() {
        PartTimeRateCreateDTO dto = baseCreateDto();
        dto.setEffectiveDate(LocalDate.of(2025, 1, 1));
        dto.setExpireDate(LocalDate.of(2024, 12, 31));

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(partTimeRateMapper, never()).insert(any(PartTimeRateDO.class));
    }

    @Test
    @DisplayName("create 失败: segment 非法抛 BAD_REQUEST")
    void create_invalidSegment() {
        PartTimeRateCreateDTO dto = baseCreateDto();
        dto.setSegment("INVALID");

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(partTimeRateMapper, never()).insert(any(PartTimeRateDO.class));
    }

    @Test
    @DisplayName("create 失败: rateCode 为空抛 BAD_REQUEST")
    void create_blankRateCode() {
        PartTimeRateCreateDTO dto = baseCreateDto();
        dto.setRateCode("");

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(partTimeRateMapper, never()).insert(any(PartTimeRateDO.class));
    }

    // ==================== getById ====================

    @Test
    @DisplayName("getById 成功")
    void getById_success() {
        PartTimeRateDO rate = new PartTimeRateDO();
        rate.setId("R1");
        rate.setRateCode("P1");
        when(partTimeRateMapper.selectById("R1")).thenReturn(rate);

        PartTimeRateDO result = service.getById("R1");

        assertEquals("P1", result.getRateCode());
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
    @DisplayName("page: 按条件分页查询返回结果")
    void page_withFilters() {
        Page<PartTimeRateDO> mockPage = new Page<>(1, 10);
        PartTimeRateDO record = new PartTimeRateDO();
        record.setRateCode("P1");
        mockPage.setRecords(List.of(record));
        when(partTimeRateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<PartTimeRateDO> result = service.page(1, 10, "P1", "SENIOR", "ACTIVE");

        assertEquals(1, result.getRecords().size());
        assertEquals("P1", result.getRecords().get(0).getRateCode());
        verify(partTimeRateMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    // ==================== matchEffective ====================

    @Test
    @DisplayName("matchEffective: 命中生效费率")
    void matchEffective_hit() {
        LocalDate date = LocalDate.of(2025, 6, 1);
        PartTimeRateDO rate = new PartTimeRateDO();
        rate.setRateCode("P5");
        rate.setVersion(1);
        when(partTimeRateMapper.selectEffective("P5", date)).thenReturn(rate);

        PartTimeRateDO result = service.matchEffective("P5", date);

        assertNotNull(result);
        assertEquals("P5", result.getRateCode());
    }

    @Test
    @DisplayName("matchEffective: 未命中返回 null")
    void matchEffective_miss() {
        LocalDate date = LocalDate.of(2025, 6, 1);
        when(partTimeRateMapper.selectEffective("P99", date)).thenReturn(null);

        PartTimeRateDO result = service.matchEffective("P99", date);

        assertNull(result);
    }

    @Test
    @DisplayName("matchEffective: 返回最新版本（mapper 已按 version DESC 取最新）")
    void matchEffective_latestVersion() {
        LocalDate date = LocalDate.of(2025, 6, 1);
        PartTimeRateDO v3 = new PartTimeRateDO();
        v3.setRateCode("P5");
        v3.setVersion(3);
        when(partTimeRateMapper.selectEffective("P5", date)).thenReturn(v3);

        PartTimeRateDO result = service.matchEffective("P5", date);

        assertEquals(3, result.getVersion());
    }

    @Test
    @DisplayName("matchEffective: rateCode 为空返回 null, 不查询")
    void matchEffective_blankCode() {
        PartTimeRateDO result = service.matchEffective("", LocalDate.now());

        assertNull(result);
        verify(partTimeRateMapper, never()).selectEffective(any(), any());
    }

    // ==================== listEffective ====================

    @Test
    @DisplayName("listEffective: 返回生效中的费率列表")
    void listEffective_returnsList() {
        LocalDate date = LocalDate.of(2025, 6, 1);
        PartTimeRateDO r1 = new PartTimeRateDO();
        r1.setRateCode("P1");
        PartTimeRateDO r2 = new PartTimeRateDO();
        r2.setRateCode("P2");
        when(partTimeRateMapper.listEffective(date)).thenReturn(List.of(r1, r2));

        List<PartTimeRateDO> result = service.listEffective(date);

        assertEquals(2, result.size());
    }

    // ==================== update ====================

    @Test
    @DisplayName("update: 更新成功")
    void update_success() {
        PartTimeRateDO exists = new PartTimeRateDO();
        exists.setId("R1");
        exists.setRateCode("P1");
        exists.setVersion(1);
        exists.setEffectiveDate(LocalDate.of(2025, 1, 1));
        when(partTimeRateMapper.selectById("R1")).thenReturn(exists);
        when(partTimeRateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        PartTimeRateUpdateDTO dto = new PartTimeRateUpdateDTO();
        dto.setRateName("P1 更新名称");
        service.update("R1", dto);

        verify(partTimeRateMapper).updateById(any(PartTimeRateDO.class));
    }

    @Test
    @DisplayName("update: 不存在抛 NOT_FOUND")
    void update_notFound() {
        when(partTimeRateMapper.selectById("X")).thenReturn(null);

        PartTimeRateUpdateDTO dto = new PartTimeRateUpdateDTO();
        dto.setRateName("x");
        BizException ex = assertThrows(BizException.class, () -> service.update("X", dto));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(partTimeRateMapper, never()).updateById(any(PartTimeRateDO.class));
    }

    @Test
    @DisplayName("update: hourlyRate <= 0 抛 BAD_REQUEST")
    void update_invalidHourlyRate() {
        PartTimeRateDO exists = new PartTimeRateDO();
        exists.setId("R1");
        exists.setRateCode("P1");
        exists.setVersion(1);
        exists.setEffectiveDate(LocalDate.of(2025, 1, 1));
        when(partTimeRateMapper.selectById("R1")).thenReturn(exists);

        PartTimeRateUpdateDTO dto = new PartTimeRateUpdateDTO();
        dto.setHourlyRate(BigDecimal.ZERO);
        BizException ex = assertThrows(BizException.class, () -> service.update("R1", dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(partTimeRateMapper, never()).updateById(any(PartTimeRateDO.class));
    }

    // ==================== delete ====================

    @Test
    @DisplayName("delete: 删除成功")
    void delete_success() {
        when(partTimeRateMapper.selectById("R1")).thenReturn(new PartTimeRateDO());

        service.delete("R1");

        verify(partTimeRateMapper).deleteById("R1");
    }

    @Test
    @DisplayName("delete: 不存在抛 NOT_FOUND")
    void delete_notFound() {
        when(partTimeRateMapper.selectById("X")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.delete("X"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(partTimeRateMapper, never()).deleteById(any());
    }
}
