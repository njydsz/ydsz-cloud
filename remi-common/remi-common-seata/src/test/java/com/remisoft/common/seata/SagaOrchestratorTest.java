package com.remisoft.common.seata;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.remisoft.common.seata.api.SagaStep;
import com.remisoft.common.seata.config.SeataProperties;
import com.remisoft.common.seata.impl.SagaOrchestrator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SagaOrchestrator} 单元测试
 *
 * @author remi-team
 * @since 1.0.0
 */
class SagaOrchestratorTest {

    private SagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        SeataProperties props = new SeataProperties();
        props.setSagaMaxRetries(2);
        props.setSagaRetryIntervalMs(10);
        orchestrator = new SagaOrchestrator(props, null, null);
    }

    @Test
    void testSuccessfulMultiStep() throws Exception {
        boolean[] executed = {false, false, false};

        List<SagaStep<Void>> steps = List.of(
            SagaStep.of("step1", () -> { executed[0] = true; return null; }, () -> {}),
            SagaStep.of("step2", () -> { executed[1] = true; return null; }, () -> {}),
            SagaStep.of("step3", () -> { executed[2] = true; return null; }, () -> {})
        );

        orchestrator.execute("test-saga", steps);

        assertTrue(executed[0]);
        assertTrue(executed[1]);
        assertTrue(executed[2]);
    }

    @Test
    void testFailureTriggersReverseCompensation() {
        boolean[] compensated = {false, false};

        List<SagaStep<Void>> steps = List.of(
            SagaStep.of("step1", () -> null, () -> { compensated[0] = true; }),
            SagaStep.of("step2", () -> { throw new RuntimeException("fail"); }, () -> { compensated[1] = true; })
        );

        assertThrows(RuntimeException.class, () -> orchestrator.execute("test-saga", steps));

        // step1 should be compensated (reverse order), step2's compensation should NOT run
        assertTrue(compensated[0], "step1 should be compensated");
        assertFalse(compensated[1], "step2 should not be compensated (it failed before completing)");
    }

    @Test
    void testTerminalStepNoCompensation() throws Exception {
        List<SagaStep<Void>> steps = List.of(
            SagaStep.terminal("step1", () -> null)
        );

        orchestrator.execute("test-saga", steps);
    }
}
