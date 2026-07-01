package com.njydsz.pmis.agent.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.agent.dto.AgentRunRequestDTO;
import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.engine.RiskWarningAgent;
import com.njydsz.pmis.agent.entity.AgentPredictionDO;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentRunStatus;
import com.njydsz.pmis.agent.enums.AgentType;
import com.njydsz.pmis.agent.mapper.AgentPredictionMapper;
import com.njydsz.pmis.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AgentServiceImpl 测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AgentServiceImpl 智能体服务")
class AgentServiceImplTest {

    private AgentPredictionMapper predictionMapper;
    private AgentServiceImpl service;

    @BeforeEach
    void setup() {
        predictionMapper = mock(AgentPredictionMapper.class);
        List<Agent> agents = List.of(
                new RiskWarningAgent()
        );
        service = new AgentServiceImpl(agents, predictionMapper);
    }

    @Test
    @DisplayName("运行风险预警")
    void runRiskWarning() {
        AgentRunRequestDTO req = new AgentRunRequestDTO();
        req.setAgentType("RISK_WARNING");
        req.setBizType("PROJECT");
        req.setBizId(1001L);
        req.setBizRef("PRJ-001");
        req.setCallerId(1L);
        req.setCallerName("tester");
        Map<String, Object> params = new HashMap<>();
        params.put("cpi", new BigDecimal("0.7"));
        params.put("spi", new BigDecimal("0.7"));
        req.setParams(params);

        when(predictionMapper.insert(any(AgentPredictionDO.class))).thenAnswer(inv -> {
            AgentPredictionDO r = inv.getArgument(0);
            r.setId(1L);
            return 1;
        });
        when(predictionMapper.updateById(any(AgentPredictionDO.class))).thenReturn(1);

        AgentPredictionDO r = service.run(req);
        assertThat(r).isNotNull();
        assertThat(r.getAgentType()).isEqualTo("RISK_WARNING");
        assertThat(r.getStatus()).isEqualTo(AgentRunStatus.SUCCESS.getCode());
        assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RED.getCode());
    }

    @Test
    @DisplayName("未知 Agent 类型")
    void unknownType() {
        AgentRunRequestDTO req = new AgentRunRequestDTO();
        req.setAgentType("UNKNOWN");
        assertThatThrownBy(() -> service.run(req))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("请求为空")
    void nullRequest() {
        assertThatThrownBy(() -> service.run(null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("内存执行")
    void executeInMemory() {
        AgentContext ctx = new AgentContext();
        ctx.setBizRef("PRJ-002");
        Map<String, Object> params = new HashMap<>();
        params.put("cpi", new BigDecimal("1.0"));
        ctx.setParams(params);
        AgentResult r = service.executeInMemory("RISK_WARNING", ctx);
        assertThat(r).isNotNull();
        assertThat(r.getAgentType()).isEqualTo(AgentType.RISK_WARNING);
    }

    @Test
    @DisplayName("内存执行-未知类型")
    void executeInMemoryUnknown() {
        assertThatThrownBy(() -> service.executeInMemory("XXX", new AgentContext()))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("按 ID 查询")
    void getById() {
        AgentPredictionDO r = new AgentPredictionDO();
        r.setId(1L);
        r.setAgentType("RISK_WARNING");
        when(predictionMapper.selectById(1L)).thenReturn(r);
        assertThat(service.getById(1L).getAgentType()).isEqualTo("RISK_WARNING");
    }

    @Test
    @DisplayName("按 ID 查询-不存在")
    void getByIdNotFound() {
        when(predictionMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("分页查询")
    void pageQuery() {
        Page<AgentPredictionDO> p = new Page<>();
        when(predictionMapper.selectPage(any(Page.class), any())).thenReturn(p);
        Page<AgentPredictionDO> r = service.page(1, 10, "RISK_WARNING", "RED", null, null, null);
        assertThat(r).isNotNull();
    }

    @Test
    @DisplayName("统计-按类型")
    void aggregateByType() {
        when(predictionMapper.aggregateByType(1L)).thenReturn(List.of(Map.of("type", "RISK_WARNING", "count", 1)));
        List<Map<String, Object>> r = service.aggregateByType(1L);
        assertThat(r).hasSize(1);
    }

    @Test
    @DisplayName("统计-告警计数")
    void countByAlert() {
        when(predictionMapper.countByAlertLevel("RED", "RISK_WARNING", 1L)).thenReturn(5L);
        long n = service.countByAlertLevel("RED", "RISK_WARNING", 1L);
        assertThat(n).isEqualTo(5L);
    }
}
