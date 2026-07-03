package com.njydsz.pmis.literule.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleStatus 枚举状态机转换矩阵测试
 *
 * <p>覆盖全部 5×5 = 25 种转换组合 + fromCode 边界情况。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
class RuleStatusTest {

    @Test
    void draftCanTransitionTo() {
        assertTrue(RuleStatus.DRAFT.canTransitionTo(RuleStatus.REVIEW));
        assertTrue(RuleStatus.DRAFT.canTransitionTo(RuleStatus.PUBLISHED));
        assertTrue(RuleStatus.DRAFT.canTransitionTo(RuleStatus.ARCHIVED));
        assertFalse(RuleStatus.DRAFT.canTransitionTo(RuleStatus.DRAFT));
        assertFalse(RuleStatus.DRAFT.canTransitionTo(RuleStatus.DISABLED));
    }

    @Test
    void reviewCanTransitionTo() {
        assertTrue(RuleStatus.REVIEW.canTransitionTo(RuleStatus.PUBLISHED));
        assertTrue(RuleStatus.REVIEW.canTransitionTo(RuleStatus.DRAFT));
        assertFalse(RuleStatus.REVIEW.canTransitionTo(RuleStatus.REVIEW));
        assertFalse(RuleStatus.REVIEW.canTransitionTo(RuleStatus.DISABLED));
        assertFalse(RuleStatus.REVIEW.canTransitionTo(RuleStatus.ARCHIVED));
    }

    @Test
    void publishedCanTransitionTo() {
        assertTrue(RuleStatus.PUBLISHED.canTransitionTo(RuleStatus.DISABLED));
        assertTrue(RuleStatus.PUBLISHED.canTransitionTo(RuleStatus.ARCHIVED));
        assertFalse(RuleStatus.PUBLISHED.canTransitionTo(RuleStatus.DRAFT));
        assertFalse(RuleStatus.PUBLISHED.canTransitionTo(RuleStatus.REVIEW));
        assertFalse(RuleStatus.PUBLISHED.canTransitionTo(RuleStatus.PUBLISHED));
    }

    @Test
    void disabledCanTransitionTo() {
        assertTrue(RuleStatus.DISABLED.canTransitionTo(RuleStatus.PUBLISHED));
        assertTrue(RuleStatus.DISABLED.canTransitionTo(RuleStatus.ARCHIVED));
        assertFalse(RuleStatus.DISABLED.canTransitionTo(RuleStatus.DRAFT));
        assertFalse(RuleStatus.DISABLED.canTransitionTo(RuleStatus.REVIEW));
        assertFalse(RuleStatus.DISABLED.canTransitionTo(RuleStatus.DISABLED));
    }

    @Test
    void archivedIsTerminalState() {
        // 已归档是终态，不能再变更到任何状态（包括自身）
        for (RuleStatus target : RuleStatus.values()) {
            assertFalse(RuleStatus.ARCHIVED.canTransitionTo(target),
                    "ARCHIVED 不应能转换到 " + target);
        }
    }

    @Test
    void fromCodeShouldParseValidValuesCaseInsensitively() {
        assertEquals(RuleStatus.DRAFT, RuleStatus.fromCode("DRAFT"));
        assertEquals(RuleStatus.DRAFT, RuleStatus.fromCode("draft"));
        assertEquals(RuleStatus.DRAFT, RuleStatus.fromCode("  Draft  "));
        assertEquals(RuleStatus.PUBLISHED, RuleStatus.fromCode("PUBLISHED"));
        assertEquals(RuleStatus.DISABLED, RuleStatus.fromCode("DISABLED"));
        assertEquals(RuleStatus.ARCHIVED, RuleStatus.fromCode("ARCHIVED"));
        assertEquals(RuleStatus.REVIEW, RuleStatus.fromCode("REVIEW"));
    }

    @Test
    void fromCodeShouldReturnNullForInvalidValues() {
        assertNull(RuleStatus.fromCode(null));
        assertNull(RuleStatus.fromCode(""));
        assertNull(RuleStatus.fromCode("   "));
        assertNull(RuleStatus.fromCode("UNKNOWN"));
        assertNull(RuleStatus.fromCode("PENDING"));
    }

    @Test
    void getDescShouldReturnChineseDescription() {
        assertEquals("草稿", RuleStatus.DRAFT.getDesc());
        assertEquals("待审核", RuleStatus.REVIEW.getDesc());
        assertEquals("已发布", RuleStatus.PUBLISHED.getDesc());
        assertEquals("已停用", RuleStatus.DISABLED.getDesc());
        assertEquals("已归档", RuleStatus.ARCHIVED.getDesc());
    }
}
