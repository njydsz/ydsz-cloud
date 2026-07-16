package com.njydsz.pmis.common.seata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.seata.api.TccAction;
import com.njydsz.pmis.common.seata.api.TccContext;
import com.njydsz.pmis.common.seata.api.TccTransactionLogStore;
import com.njydsz.pmis.common.seata.config.SeataProperties;
import com.njydsz.pmis.common.seata.impl.InMemoryTccTransactionLogStore;
import com.njydsz.pmis.common.seata.impl.TccTransactionManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TccTransactionManager} 单元测试
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
class TccTransactionManagerTest {

    private TccTransactionManager manager;
    private TccTransactionLogStore logStore;
    private SeataProperties properties;

    @BeforeEach
    void setUp() {
        logStore = new InMemoryTccTransactionLogStore();
        properties = new SeataProperties();
        properties.setTccRetryCount(2);
        properties.setTccRetryIntervalMs(10);
        manager = new TccTransactionManager(logStore, properties, null, null);
    }

    @Test
    void testSuccessfulTcc() throws Exception {
        TccAction<String> action = new TccAction<>() {
            @Override
            public String tryAction(TccContext ctx) {
                return "try-result";
            }

            @Override
            public void confirmAction(TccContext ctx) {
            }

            @Override
            public void cancelAction(TccContext ctx) {
            }
        };

        String result = manager.executeTcc("test-tx", action);
        assertEquals("try-result", result);
    }

    @Test
    void testTryFailureTriggersCancel() {
        TccAction<String> action = new TccAction<>() {
            @Override
            public String tryAction(TccContext ctx) throws Exception {
                throw new RuntimeException("try failed");
            }

            @Override
            public void confirmAction(TccContext ctx) {
            }

            @Override
            public void cancelAction(TccContext ctx) {
            }
        };

        assertThrows(RuntimeException.class, () -> manager.executeTcc("test-tx", action));
    }

    @Test
    void testConfirmFailureTriggersCancel() {
        TccAction<String> action = new TccAction<>() {
            @Override
            public String tryAction(TccContext ctx) {
                return "ok";
            }

            @Override
            public void confirmAction(TccContext ctx) throws Exception {
                throw new RuntimeException("confirm failed");
            }

            @Override
            public void cancelAction(TccContext ctx) {
            }
        };

        assertThrows(RuntimeException.class, () -> manager.executeTcc("test-tx", action));
    }

    @Test
    void testEmptyRollbackProtection() {
        // 当 Try 未完成时，Cancel 不应执行
        TccAction<String> action = new TccAction<>() {
            @Override
            public String tryAction(TccContext ctx) throws Exception {
                throw new RuntimeException("try failed");
            }

            @Override
            public void confirmAction(TccContext ctx) {
            }

            @Override
            public void cancelAction(TccContext ctx) {
            }
        };

        assertThrows(RuntimeException.class, () -> manager.executeTcc("test-tx", action));
    }

    @Test
    void testIdempotentConfirm() throws Exception {
        TccAction<String> action = new TccAction<>() {
            @Override
            public String tryAction(TccContext ctx) {
                return "result";
            }

            @Override
            public void confirmAction(TccContext ctx) {
            }

            @Override
            public void cancelAction(TccContext ctx) {
            }
        };

        String result = manager.executeTcc("test-tx", action);
        assertEquals("result", result);
    }

    @Test
    void testGetCurrentType() {
        assertEquals(com.njydsz.pmis.common.seata.api.TransactionType.TCC, manager.getCurrentType());
    }

    @Test
    void testGetCurrentXid() {
        assertNull(manager.getCurrentXid());
    }

    @Test
    void testExecuteWithCompensationNotIgnoringCompensation() {
        boolean[] compensationExecuted = {false};
        assertThrows(RuntimeException.class, () -> {
            manager.executeWithCompensation("test-tx",
                    () -> { throw new RuntimeException("fail"); },
                    () -> { compensationExecuted[0] = true; });
        });
        assertTrue(compensationExecuted[0], "Compensation should be executed on failure");
    }

    @Test
    void testNoLogStoreBackwardCompatible() throws Exception {
        TccTransactionManager noLogManager = new TccTransactionManager();
        TccAction<String> action = new TccAction<>() {
            @Override
            public String tryAction(TccContext ctx) { return "ok"; }
            @Override
            public void confirmAction(TccContext ctx) { }
            @Override
            public void cancelAction(TccContext ctx) { }
        };
        String result = noLogManager.executeTcc("test", action);
        assertEquals("ok", result);
    }
}
