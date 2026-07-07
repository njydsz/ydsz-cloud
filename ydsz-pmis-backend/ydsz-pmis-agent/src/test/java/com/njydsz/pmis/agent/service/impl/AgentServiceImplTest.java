package com.njydsz.pmis.agent.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.agent.dto.AgentRunRequestDTO;
import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.entity.AgentPredictionDO;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentRunStatus;
import com.njydsz.pmis.agent.enums.AgentType;
import com.njydsz.pmis.agent.mapper.AgentPredictionMapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.TraceIdUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AgentServiceImpl 单元测试
 *
 * @author ydsz-pmis-team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgentServiceImpl 单元测试")
class AgentServiceImplTest {

    @Mock
    private Agent riskWarningAgent;

    @Mock
    private AgentPredictionMapper predictionMapper;

    private AgentServiceImpl agentService;

    private MockedStatic<TenantContext> tenantContextMock;
    private MockedStatic<TraceIdUtil> traceIdUtilMock;

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getTenantId).thenReturn(1L);

        traceIdUtilMock = mockStatic(TraceIdUtil.class);
        traceIdUtilMock.when(TraceIdUtil::get).thenReturn("test-trace-id");

        when(riskWarningAgent.type()).thenReturn(AgentType.RISK_WARNING);

        agentService = new AgentServiceImpl(List.of(riskWarningAgent), predictionMapper);
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
        traceIdUtilMock.close();
    }

    // ==================== run ====================

    @Test
    @DisplayName("run - 正常执行 Agent 并返回预测记录")
    void run_shouldExecuteAgentAndReturnPrediction() {
        AgentRunRequestDTO req = buildRequest("RISK_WARNING", "100", "PROJECT");
        AgentResult mockResult = buildAgentResult(AgentAlertLevel.YELLOW, new BigDecimal("75.0"), new BigDecimal("0.85"));

        when(riskWarningAgent.execute(any(AgentContext.class))).thenReturn(mockResult);

        AgentPredictionDO result = agentService.run(req);

        assertNotNull(result);
        assertEquals(AgentRunStatus.SUCCESS.getCode(), result.getStatus());
        assertEquals(AgentType.RISK_WARNING.getCode(), result.getAgentType());
        assertEquals(AgentAlertLevel.YELLOW.getCode(), result.getAlertLevel());
        verify(predictionMapper).insert(any(AgentPredictionDO.class));
        verify(predictionMapper).updateById(any(AgentPredictionDO.class));
    }

    @Test
    @DisplayName("run - 请求为 null 时抛出 BizException")
    void run_shouldThrowBizExceptionWhenRequestIsNull() {
        BizException ex = assertThrows(BizException.class, () -> agentService.run(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("run - 无效 Agent 类型时抛出 BizException")
    void run_shouldThrowBizExceptionWhenAgentTypeInvalid() {
        AgentRunRequestDTO req = buildRequest("INVALID_TYPE", "100", "PROJECT");

        BizException ex = assertThrows(BizException.class, () -> agentService.run(req));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("run - Agent 执行失败时更新状态为 FAILED 并抛出异常")
    void run_shouldMarkFailedWhenAgentThrows() {
        AgentRunRequestDTO req = buildRequest("RISK_WARNING", "100", "PROJECT");

        when(riskWarningAgent.execute(any(AgentContext.class)))
                .thenThrow(new RuntimeException("Agent execution error"));

        BizException ex = assertThrows(BizException.class, () -> agentService.run(req));
        assertEquals(BizErrorCode.INTERNAL_ERROR.getCode(), ex.getCode());

        ArgumentCaptor<AgentPredictionDO> captor = ArgumentCaptor.forClass(AgentPredictionDO.class);
        verify(predictionMapper, atLeastOnce()).updateById(captor.capture());
        List<AgentPredictionDO> updates = captor.getAllValues();
        AgentPredictionDO lastUpdate = updates.get(updates.size() - 1);
        assertEquals(AgentRunStatus.FAILED.getCode(), lastUpdate.getStatus());
    }

    @Test
    @DisplayName("run - 无 source 时默认设置为 MANUAL")
    void run_shouldDefaultSourceToManual() {
        AgentRunRequestDTO req = buildRequest("RISK_WARNING", "100", "PROJECT");
        req.setSource(null);

        AgentResult mockResult = buildAgentResult(AgentAlertLevel.NORMAL, new BigDecimal("50.0"), new BigDecimal("0.9"));
        when(riskWarningAgent.execute(any(AgentContext.class))).thenReturn(mockResult);

        AgentPredictionDO result = agentService.run(req);

        assertNotNull(result);
        assertEquals("MANUAL", result.getSource());
    }

    // ==================== executeInMemory ====================

    @Test
    @DisplayName("executeInMemory - 正常执行并返回 AgentResult")
    void executeInMemory_shouldReturnAgentResult() {
        AgentContext ctx = new AgentContext("PROJECT", "100", "ref", "1", "user", "MANUAL", null);
        AgentResult expectedResult = buildAgentResult(AgentAlertLevel.NORMAL, new BigDecimal("80.0"), new BigDecimal("0.9"));

        when(riskWarningAgent.execute(any(AgentContext.class))).thenReturn(expectedResult);

        AgentResult result = agentService.executeInMemory("RISK_WARNING", ctx);

        assertNotNull(result);
        assertEquals(AgentAlertLevel.NORMAL, result.getAlertLevel());
        assertEquals(new BigDecimal("80.0"), result.getScore());
    }

    @Test
    @DisplayName("executeInMemory - 无效 Agent 类型时抛出 BizException")
    void executeInMemory_shouldThrowBizExceptionWhenAgentTypeInvalid() {
        AgentContext ctx = new AgentContext("PROJECT", "100", "ref", "1", "user", "MANUAL", null);

        BizException ex = assertThrows(BizException.class,
                () -> agentService.executeInMemory("INVALID", ctx));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ==================== getById ====================

    @Test
    @DisplayName("getById - 查询存在的记录返回实体")
    void getById_shouldReturnEntityWhenFound() {
        AgentPredictionDO record = new AgentPredictionDO();
        record.setId("1");
        record.setAgentType(AgentType.RISK_WARNING.getCode());
        record.setStatus(AgentRunStatus.SUCCESS.getCode());

        when(predictionMapper.selectById(1L)).thenReturn(record);

        AgentPredictionDO result = agentService.getById(1L);

        assertNotNull(result);
        assertEquals("1", result.getId());
        assertEquals(AgentType.RISK_WARNING.getCode(), result.getAgentType());
    }

    @Test
    @DisplayName("getById - 记录不存在时抛出 BizException")
    void getById_shouldThrowBizExceptionWhenNotFound() {
        when(predictionMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> agentService.getById(999L));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== page ====================

    @Test
    @DisplayName("page - 分页查询带过滤条件返回结果")
    void page_shouldReturnPageWithFilters() {
        Page<AgentPredictionDO> mockPage = new Page<>(1, 10);
        when(predictionMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        Page<AgentPredictionDO> result = agentService.page(1, 10, "RISK_WARNING", "YELLOW", "SUCCESS", "PROJECT", "100");

        assertNotNull(result);
        assertEquals(1, result.getCurrent());
        assertEquals(10, result.getSize());
    }

    // ==================== listRecent ====================

    @Test
    @DisplayName("listRecent - 查询最近记录返回列表")
    void listRecent_shouldReturnRecentRecords() {
        List<AgentPredictionDO> mockList = List.of(new AgentPredictionDO());
        when(predictionMapper.selectByAgentType("RISK_WARNING", "YELLOW", 20)).thenReturn(mockList);

        List<AgentPredictionDO> result = agentService.listRecent("RISK_WARNING", "YELLOW", null);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("listRecent - limit 为 null 时默认 20")
    void listRecent_shouldDefaultLimitTo20() {
        when(predictionMapper.selectByAgentType("RISK_WARNING", null, 20)).thenReturn(Collections.emptyList());

        List<AgentPredictionDO> result = agentService.listRecent("RISK_WARNING", null, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== aggregateByType ====================

    @Test
    @DisplayName("aggregateByType - 聚合查询返回结果")
    void aggregateByType_shouldReturnAggregation() {
        List<Map<String, Object>> mockList = List.of(Map.of("agentType", "RISK_WARNING", "count", 5L));
        when(predictionMapper.aggregateByType(1L)).thenReturn(mockList);

        List<Map<String, Object>> result = agentService.aggregateByType(null);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    // ==================== countByAlertLevel ====================

    @Test
    @DisplayName("countByAlertLevel - 统计告警等级数量")
    void countByAlertLevel_shouldReturnCount() {
        when(predictionMapper.countByAlertLevel("RED", "RISK_WARNING", 1L)).thenReturn(3L);

        long result = agentService.countByAlertLevel("RED", "RISK_WARNING", null);

        assertEquals(3L, result);
    }

    // ==================== helper ====================

    private AgentRunRequestDTO buildRequest(String agentType, String bizId, String bizType) {
        AgentRunRequestDTO req = new AgentRunRequestDTO();
        req.setAgentType(agentType);
        req.setBizType(bizType);
        req.setBizId(bizId);
        req.setBizRef("TEST-REF");
        req.setCallerId("1");
        req.setCallerName("测试用户");
        req.setSource("MANUAL");
        req.setParams(Map.of("key", "value"));
        return req;
    }

    private AgentResult buildAgentResult(AgentAlertLevel alertLevel, BigDecimal score, BigDecimal confidence) {
        AgentResult result = new AgentResult();
        result.setAgentType(AgentType.RISK_WARNING);
        result.setAlertLevel(alertLevel);
        result.setScore(score);
        result.setConfidence(confidence);
        result.setSuggestion("建议措施");
        result.setMatchedRules(List.of("rule1", "rule2"));
        result.setPayload(Map.of("detail", "output"));
        return result;
    }
}