package com.njydsz.pmis.agent.service.impl;

import com.njydsz.pmis.agent.dto.AgentRunRequestDTO;
import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.engine.trace.AgentTracer;
import com.njydsz.pmis.agent.entity.AgentPredictionDO;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import com.njydsz.pmis.agent.mapper.AgentPredictionMapper;
import com.njydsz.pmis.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentServiceImpl#runAsync} 异步执行 TenantContext 透传测试（P1-3 修复验证）。
 *
 * <p>验证：
 * <ul>
 *   <li>主线程的 TenantContext 被捕获并透传到异步线程</li>
 *   <li>异步线程执行 run(req) 时 TenantContext 与主线程一致</li>
 *   <li>异步线程执行完毕后清理 TenantContext，避免线程池复用导致租户串号</li>
 * </ul>
 *
 * <p>实现方式：mock {@link ThreadPoolTaskExecutor#execute} 捕获提交的 {@link Runnable}，
 * 在主线程清除 TenantContext 后再执行捕获的任务，模拟异步线程无 ThreadLocal 继承的场景。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-3)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgentServiceImpl.runAsync TenantContext 透传测试")
class AgentServiceImplRunAsyncTest {

    @Mock
    private AgentPredictionMapper predictionMapper;
    @Mock
    private ThreadPoolTaskExecutor agentExecutor;

    private Agent agent;
    private AgentServiceImpl service;

    @BeforeEach
    void setUp() {
        agent = mock(Agent.class);
        when(agent.type()).thenReturn(AgentType.RISK_WARNING);
        when(agent.execute(any())).thenReturn(new AgentResult(
                AgentType.RISK_WARNING, AgentAlertLevel.NORMAL,
                new BigDecimal("0.5"), new BigDecimal("0.8"), "ok", List.of(), Map.of()));
        // predictionMapper.insert 返回 1（表示插入成功）
        when(predictionMapper.insert(any(AgentPredictionDO.class))).thenReturn(1);
        when(predictionMapper.updateById(any(AgentPredictionDO.class))).thenReturn(1);

        service = new AgentServiceImpl(List.of(agent), predictionMapper,
                AgentTracer.noOp(), agentExecutor);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** 构造测试请求 */
    private AgentRunRequestDTO req() {
        AgentRunRequestDTO req = new AgentRunRequestDTO();
        req.setAgentType(AgentType.RISK_WARNING.getCode());
        req.setBizType("PROJECT");
        req.setBizId("B001");
        req.setBizRef("REF-001");
        req.setCallerId("user-001");
        req.setCallerName("测试用户");
        req.setSource("MANUAL");
        return req;
    }

    @Test
    @DisplayName("P1-3: 异步线程继承主线程的 TenantContext")
    void shouldPropagateTenantContextToAsyncThread() {
        // 1. 主线程设置租户 ID
        TenantContext.setTenantId("tenant-async-001");
        final Runnable[] capturedTask = new Runnable[1];
        doAnswer(invocation -> {
            capturedTask[0] = invocation.getArgument(0);
            return null;
        }).when(agentExecutor).execute(any(Runnable.class));

        // 2. 调用 runAsync（此时在主线程，捕获 tenantId）
        service.runAsync(req());

        // 3. 清除主线程的 TenantContext，模拟异步线程无 ThreadLocal 继承
        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);

        // 4. 执行捕获的异步任务
        assertThat(capturedTask[0]).as("agentExecutor.execute 应被调用").isNotNull();
        capturedTask[0].run();

        // 5. 验证异步线程中 run(req) 执行时，TenantContext 被恢复为主线程的值
        ArgumentCaptor<AgentPredictionDO> recordCaptor = ArgumentCaptor.forClass(AgentPredictionDO.class);
        verify(predictionMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getTenantId()).isEqualTo("tenant-async-001");

        // 6. 验证异步线程执行完毕后清理 TenantContext
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    @Test
    @DisplayName("P1-3: runAsync 异常时仍清理 TenantContext")
    void shouldCleanTenantContextEvenOnFailure() {
        // 使用无效 agentType，run(req) 会抛 BizException
        AgentRunRequestDTO invalidReq = req();
        invalidReq.setAgentType("INVALID_TYPE");

        TenantContext.setTenantId("tenant-fail");
        final Runnable[] capturedTask = new Runnable[1];
        doAnswer(invocation -> {
            capturedTask[0] = invocation.getArgument(0);
            return null;
        }).when(agentExecutor).execute(any(Runnable.class));

        // runAsync 不会抛异常（内部 catch）
        service.runAsync(invalidReq);

        TenantContext.clear();
        // 执行异步任务（run 会抛异常，但 runAsync 内部 catch）
        capturedTask[0].run();

        // 验证 TenantContext 被清理
        assertThat(TenantContext.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    @Test
    @DisplayName("P1-3: agentExecutor.execute 被调用")
    void shouldSubmitTaskToAgentExecutor() {
        TenantContext.setTenantId("tenant-submit");
        service.runAsync(req());

        verify(agentExecutor).execute(any(Runnable.class));
    }
}
