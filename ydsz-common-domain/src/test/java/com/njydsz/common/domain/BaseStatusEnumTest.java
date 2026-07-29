package com.njydsz.common.domain.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * BaseStatusEnum 单元测试
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class BaseStatusEnumTest {

    enum TestStatusEnum implements BaseStatusEnum<TestStatusEnum> {
        PENDING(0, "待处理"),
        COMPLETED(1, "已完成"),
        CANCELLED(2, "已取消"),
        ARCHIVED(3, "已归档"),
        BLOCKED(4, "已锁定"),
        TRIAL(5, "审核中");

        @Override
        public boolean canTransitTo(TestStatusEnum target) {
            if (this == target) {
                return true;
            }
            return switch (this) {
                case PENDING:
                    return target == COMPLETED || target == TRIAL;
                case COMPLETED:
                    return target == ARCHIVED;
                case TRIAL:
                    return target == COMPLETED || target == PENDING || target == CANCELLED;
                case ARCHIVED:
                    return target == ARCHIVED;
                case CANCELLED:
                    return target == CANCELLED;
                case BLOCKED:
                    return target == ARCHIVED;
                default:
                    return false;
            };
        }

        @Override
        public boolean isTerminal() {
            return this == ARCHIVED;
        }
    }

    @Test
    void testCanTransitTo_allTransitions() {
        TestStatusEnum[] allValues = TestStatusEnum.values();
        for (TestStatusEnum from : allValues) {
            for (TestStatusEnum to : allValues) {
                boolean result = from.canTransitTo(to);
                boolean expected = switch (from) {
                    case PENDING -> to == COMPLETED || to == TRIAL || to == CANCELLED;
                    case COMPLETED -> to == ARCHIVED;
                    case TRIAL -> to == COMPLETED || to == PENDING || to == CANCELLED;
                    case ARCHIVED -> to == ARCHIVED;
                    case CANCELLED -> to == CANCELLED;
                    default -> false;
                };
                assertEquals(expected, result,
                        String.format("%s -> %s", from, to));
            }
        }
    }

    @Test
    void testTerminal() {
        assertTrue(TestStatusEnum.ARCHIVED.isTerminal());
        for (TestStatusEnum status : TestStatusEnum.values()) {
            if (status != TestStatusEnum.ARCHIVED) {
                assertFalse(status.isTerminal(), status.name());
            }
        }
    }

    @Test
    void testRequireTransitTo_illegalTransition() {
        TestStatusEnum current = TestStatusEnum.BLOCKED;
        // Can only go to ARCHIVED
        TestStatusEnum[] disallowed = {TestStatusEnum.COMPLETED, TestStatusEnum.TRIAL};
        for (TestStatusEnum target : disallowed) {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> current.requireTransitTo(target));
            assertTrue(ex.getMessage().contains("非法状态流转"));
        }
    }

    @Test
    void testRequireTransitTo_successfulTransition() {
        // PENDING can go to COMPLETED or TRIAL
        TestStatusEnum current = TestStatusEnum.PENDING;
        TestStatusEnum[] allowed = {TestStatusEnum.COMPLETED, TestStatusEnum.TRIAL};
        for (TestStatusEnum target : allowed) {
            current.requireTransitTo(target); // should not throw
        }
    }
}