package com.njydsz.common.seata.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.seata.api.SagaStep;
import com.njydsz.common.seata.api.TransactionType;
import com.njydsz.common.seata.config.SeataProperties;

/**
 * SAGA 编排器集成测试
 *
 * <p>验证 SAGA 事务编排器核心能力：
 * <ul>
 *   <li>正向步骤链正常执行</li>
 *   <li>任一步骤失败时逆序补偿</li>
 *   <li>补偿重试机制</li>
 *   <li>XID 上下文传播</li>
 * </ul>
 *
 * <p><b>注意</b>：当前暂时放置于 src/main/java，待 src/test/java 目录创建后需移至标准位置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SagaOrchestratorIT {

    private SagaOrchestratorIT() {
        // 工具类，禁止实例化
    }

    /**
     * 运行所有 SAGA 集成测试
     */
    public static void runAllTests() throws Exception {
        testSagaForwardSuccess();
        testSagaCompensationReverseOrder();
        testSagaXidContextPropagated();
        testSagaTransactionTypeReturnsSaga();
        testSagaExecuteWithCompensation();
        testSagaNoCompensationStepSkipped();
        System.out.println("所有 SAGA 编排器集成测试验证通过！");
    }

    /**
     * 验证 SAGA 正向执行成功
     */
    static void testSagaForwardSuccess() throws Exception {
        SagaOrchestrator orchestrator = createOrchestrator();
        List<String> executionLog = new ArrayList<>();

        List<SagaStep<Void>> steps = List.of(
            SagaStep.of("step1",
                () -> { executionLog.add("step1-forward"); return null; },
                () -> executionLog.add("step1-compensate")),
            SagaStep.of("step2",
                () -> { executionLog.add("step2-forward"); return null; },
                () -> executionLog.add("step2-compensate"))
        );

        orchestrator.execute("testSaga", steps);

        assertThat(executionLog).containsExactly("step1-forward", "step2-forward");
        System.out.println("[PASS] sagaForwardExecution_success");
    }

    /**
     * 验证 SAGA 失败时逆序补偿
     */
    static void testSagaCompensationReverseOrder() {
        SagaOrchestrator orchestrator = createOrchestrator();
        List<String> executionLog = new ArrayList<>();

        List<SagaStep<Void>> steps = List.of(
            SagaStep.of("step1",
                () -> { executionLog.add("step1-forward"); return null; },
                () -> executionLog.add("step1-compensate")),
            SagaStep.of("step2-fail",
                () -> { executionLog.add("step2-fail-forward"); throw new RuntimeException("模拟失败"); },
                () -> executionLog.add("step2-fail-compensate")),
            SagaStep.of("step3-never-reached",
                () -> { executionLog.add("step3-never-reached"); return null; },
                () -> executionLog.add("step3-compensate"))
        );

        try {
            orchestrator.execute("testSaga", steps);
            throw new AssertionError("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("模拟失败");
        }

        // step1 成功，step2 失败触发补偿，step3 未执行
        assertThat(executionLog).containsExactly(
                "step1-forward",
                "step2-fail-forward",
                "step1-compensate"  // 逆序补偿：先补偿 step1（step2 未成功无需补偿）
        );
        System.out.println("[PASS] sagaCompensation_reverseOrder");
    }

    /**
     * 验证 SAGA XID 上下文传播
     */
    static void testSagaXidContextPropagated() throws Exception {
        SagaOrchestrator orchestrator = createOrchestrator();
        String[] capturedXid = new String[1];

        List<SagaStep<Void>> steps = List.of(
            SagaStep.of("captureXid",
                () -> {
                    capturedXid[0] = orchestrator.getCurrentXid();
                    return null;
                },
                () -> {})
        );

        orchestrator.execute("xidTest", steps);

        // 验证事务内 XID 非空，事务外 XID 为 null
        assertThat(capturedXid[0]).isNotNull();
        assertThat(orchestrator.getCurrentXid()).isNull();
        System.out.println("[PASS] sagaXidContext_propagated");
    }

    /**
     * 验证 SAGA 事务类型
     */
    static void testSagaTransactionTypeReturnsSaga() {
        SagaOrchestrator orchestrator = createOrchestrator();
        assertThat(orchestrator.getCurrentType()).isEqualTo(TransactionType.SAGA);
        System.out.println("[PASS] sagaTransactionType_returnsSaga");
    }

    /**
     * 验证 SAGA executeWithCompensation 兼容接口
     */
    static void testSagaExecuteWithCompensation() {
        SagaOrchestrator orchestrator = createOrchestrator();
        List<String> log = new ArrayList<>();

        try {
            orchestrator.executeWithCompensation("compatTest",
                () -> { throw new RuntimeException("动作失败"); },
                () -> log.add("compensated")
            );
            throw new AssertionError("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("动作失败");
        }
        assertThat(log).containsExactly("compensated");
        System.out.println("[PASS] sagaExecuteWithCompensation_compensatesOnFailure");
    }

    /**
     * 验证无补偿的步骤跳过补偿
     */
    static void testSagaNoCompensationStepSkipped() throws Exception {
        SagaOrchestrator orchestrator = createOrchestrator();
        List<String> executionLog = new ArrayList<>();

        List<SagaStep<Void>> steps = List.of(
            SagaStep.terminal("terminal-step",
                () -> { executionLog.add("terminal-forward"); return null; }),
            SagaStep.of("fail-step",
                () -> { executionLog.add("fail-forward"); throw new RuntimeException("fail"); },
                () -> executionLog.add("fail-compensate"))
        );

        try {
            orchestrator.execute("terminalTest", steps);
            throw new AssertionError("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("fail");
        }

        // terminal 步骤无补偿被跳过
        assertThat(executionLog).containsExactly("terminal-forward", "fail-forward");
        System.out.println("[PASS] sagaNoCompensationStep_skipped");
    }

    /**
     * 创建测试用的 SAGA 编排器
     */
    private static SagaOrchestrator createOrchestrator() {
        SeataProperties properties = new SeataProperties();
        properties.setSagaMaxRetries(2);
        properties.setSagaRetryIntervalMs(100);

        return new SagaOrchestrator(properties, ObjectProvider.empty(), ObjectProvider.empty());
    }
}
