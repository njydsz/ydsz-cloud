package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.user.dto.BenchRecordCreateDTO;
import com.njydsz.pmis.user.entity.BenchRecordDO;
import com.njydsz.pmis.user.mapper.BenchRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BenchServiceImpl 测试
 */
@DisplayName("BenchServiceImpl 闲置池")
class BenchServiceImplTest {

    private BenchRecordMapper mapper;
    private BenchServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(BenchRecordMapper.class);
        service = new BenchServiceImpl(mapper);
    }

    @Test
    @DisplayName("act ENTER 入池")
    void act_enter() {
        BenchRecordCreateDTO dto = baseDto();
        dto.setAction("ENTER");
        when(mapper.selectByCode("B-1")).thenReturn(null);
        when(mapper.selectActiveByEmployee(1L)).thenReturn(null);
        when(mapper.insert(any(BenchRecordDO.class))).thenAnswer(inv -> {
            BenchRecordDO b = inv.getArgument(0);
            b.setId(20L);
            return 1;
        });
        Long id = service.act(dto);
        assertThat(id).isEqualTo(20L);
    }

    @Test
    @DisplayName("act 重复入池抛异常")
    void act_enterDuplicate() {
        BenchRecordCreateDTO dto = baseDto();
        dto.setAction("ENTER");
        when(mapper.selectByCode("B-1")).thenReturn(null);
        when(mapper.selectActiveByEmployee(1L)).thenReturn(new BenchRecordDO());
        assertThatThrownBy(() -> service.act(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("act EXIT 无活跃记录直接返回")
    void act_exitWithoutActive() {
        BenchRecordCreateDTO dto = baseDto();
        dto.setAction("EXIT");
        when(mapper.selectByCode("B-1")).thenReturn(null);
        when(mapper.selectActiveByEmployee(1L)).thenReturn(null);
        service.act(dto);
    }

    @Test
    @DisplayName("act EXIT 关闭活跃记录并算成本")
    void act_exitUpdates() {
        BenchRecordCreateDTO dto = baseDto();
        dto.setAction("EXIT");
        BenchRecordDO active = new BenchRecordDO();
        active.setId(3L);
        active.setBenchCode("B-3");
        active.setBenchDate(LocalDate.now().minusDays(10));
        active.setDailyCost(new BigDecimal("500"));
        when(mapper.selectByCode("B-1")).thenReturn(null);
        when(mapper.selectActiveByEmployee(1L)).thenReturn(active);
        when(mapper.updateById(any(BenchRecordDO.class))).thenReturn(1);
        service.act(dto);
        assertThat(active.getIdleDays()).isEqualTo(10);
        assertThat(active.getTotalIdleCost()).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    @Test
    @DisplayName("act 编号重复抛 DUPLICATE_KEY")
    void act_duplicate() {
        BenchRecordCreateDTO dto = baseDto();
        when(mapper.selectByCode("B-1")).thenReturn(new BenchRecordDO());
        assertThatThrownBy(() -> service.act(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("act 未知动作抛 BAD_REQUEST")
    void act_unknownAction() {
        BenchRecordCreateDTO dto = baseDto();
        dto.setAction("DANCE");
        when(mapper.selectByCode(any())).thenReturn(null);
        assertThatThrownBy(() -> service.act(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("autoExit null 员工抛 BAD_REQUEST")
    void autoExit_null() {
        assertThatThrownBy(() -> service.autoExit(null, null, null, null))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("autoExit 无活跃不报错")
    void autoExit_noActive() {
        when(mapper.selectActiveByEmployee(2L)).thenReturn(null);
        service.autoExit(2L, null, null, null);
    }

    @Test
    @DisplayName("getById 不存在抛 NOT_FOUND")
    void getById_notFound() {
        when(mapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(1L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("aggregateByPool 委托 mapper")
    void aggregate() {
        when(mapper.aggregateByPool("ACTIVE")).thenReturn(List.of(Map.of("pool_id", 1)));
        assertThat(service.aggregateByPool()).hasSize(1);
    }

    @Test
    @DisplayName("totalIdleCost 累加")
    void totalCost() {
        when(mapper.aggregateByPool("ACTIVE")).thenReturn(List.of(
                Map.of("total_cost", new BigDecimal("100.50")),
                Map.of("total_cost", new BigDecimal("200.25"))
        ));
        assertThat(service.totalIdleCost()).isEqualByComparingTo(new BigDecimal("300.75"));
    }

    @Test
    @DisplayName("dashboard 返回三段")
    void dashboard() {
        when(mapper.aggregateByPool("ACTIVE")).thenReturn(List.of());
        when(mapper.flowByDateRange(any(), any())).thenReturn(List.of());
        Map<String, Object> out = service.dashboard();
        assertThat(out).containsKeys("activePools", "totalIdleCost", "recentFlow");
    }

    private BenchRecordCreateDTO baseDto() {
        BenchRecordCreateDTO d = new BenchRecordCreateDTO();
        d.setBenchCode("B-1");
        d.setEmployeeId(1L);
        d.setBenchDate(LocalDate.now());
        d.setDailyCost(new BigDecimal("500"));
        return d;
    }
}
