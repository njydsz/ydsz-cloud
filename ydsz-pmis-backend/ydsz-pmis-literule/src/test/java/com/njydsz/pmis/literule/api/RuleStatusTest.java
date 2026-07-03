package com.njydsz.pmis.literule.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleStatus 枚举测试
 *
 * <p>覆盖状态机转换合法性、fromCode 安全解析。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@DisplayName("RuleStatus 生命周期状态机测试")
class RuleStatusTest {

    @Nested
    @DisplayName("状态机转换 canTransitionTo")
    class TransitionTest {

        @Test
        @DisplayName("DRAFT 可以转到 REVIEW/PUBLISHED/ARCHIVED")
        void draftTransitions() {
            assertTrue(RuleStatus.DRAFT.canTransitionTo(RuleStatus.REVIEW));
            assertTrue(RuleStatus.DRAFT.canTransitionTo(RuleStatus.PUBLISHED));
            assertTrue(RuleStatus.DRAFT.canTransitionTo(RuleStatus.ARCHIVED));
            assertFalse(RuleStatus.DRAFT.canTransitionTo(RuleStatus.DISABLED));
        }

        @Test
        @DisplayName("REVIEW 可以转到 PUBLISHED/DRAFT")
        void reviewTransitions() {
            assertTrue(RuleStatus.REVIEW.canTransitionTo(RuleStatus.PUBLISHED));
            assertTrue(RuleStatus.REVIEW.canTransitionTo(RuleStatus.DRAFT));
            assertFalse(RuleStatus.REVIEW.canTransitionTo(RuleStatus.DISABLED));
            assertFalse(RuleStatus.REVIEW.canTransitionTo(RuleStatus.ARCHIVED));
        }

        @Test
        @DisplayName("PUBLISHED 可以转到 DISABLED/ARCHIVED")
        void publishedTransitions() {
            assertTrue(RuleStatus.PUBLISHED.canTransitionTo(RuleStatus.DISABLED));
            assertTrue(RuleStatus.PUBLISHED.canTransitionTo(RuleStatus.ARCHIVED));
            assertFalse(RuleStatus.PUBLISHED.canTransitionTo(RuleStatus.DRAFT));
            assertFalse(RuleStatus.PUBLISHED.canTransitionTo(RuleStatus.REVIEW));
        }

        @Test
        @DisplayName("DISABLED 可以转到 PUBLISHED/ARCHIVED")
        void disabledTransitions() {
            assertTrue(RuleStatus.DISABLED.canTransitionTo(RuleStatus.PUBLISHED));
            assertTrue(RuleStatus.DISABLED.canTransitionTo(RuleStatus.ARCHIVED));
            assertFalse(RuleStatus.DISABLED.canTransitionTo(RuleStatus.DRAFT));
        }

        @Test
        @DisplayName("ARCHIVED 是终态，不可再转换")
        void archivedIsTerminal() {
            assertFalse(RuleStatus.ARCHIVED.canTransitionTo(RuleStatus.DRAFT));
            assertFalse(RuleStatus.ARCHIVED.canTransitionTo(RuleStatus.REVIEW));
            assertFalse(RuleStatus.ARCHIVED.canTransitionTo(RuleStatus.PUBLISHED));
            assertFalse(RuleStatus.ARCHIVED.canTransitionTo(RuleStatus.DISABLED));
            assertFalse(RuleStatus.ARCHIVED.canTransitionTo(RuleStatus.ARCHIVED));
        }
    }

    @Nested
    @DisplayName("fromCode 安全解析")
    class FromCodeTest {

        @Test
        @DisplayName("合法编码正确解析")
        void shouldParseValidCode() {
            assertEquals(RuleStatus.DRAFT, RuleStatus.fromCode("DRAFT"));
            assertEquals(RuleStatus.REVIEW, RuleStatus.fromCode("REVIEW"));
            assertEquals(RuleStatus.PUBLISHED, RuleStatus.fromCode("PUBLISHED"));
            assertEquals(RuleStatus.DISABLED, RuleStatus.fromCode("DISABLED"));
            assertEquals(RuleStatus.ARCHIVED, RuleStatus.fromCode("ARCHIVED"));
        }

        @Test
        @DisplayName("大小写不敏感解析")
        void shouldParseCaseInsensitive() {
            assertEquals(RuleStatus.DRAFT, RuleStatus.fromCode("draft"));
            assertEquals(RuleStatus.PUBLISHED, RuleStatus.fromCode("published"));
            assertEquals(RuleStatus.REVIEW, RuleStatus.fromCode("Review"));
        }

        @Test
        @DisplayName("带空白的编码正确解析")
        void shouldParseWithWhitespace() {
            assertEquals(RuleStatus.DRAFT, RuleStatus.fromCode("  DRAFT  "));
        }

        @Test
        @DisplayName("null 返回 null")
        void shouldReturnNullForNull() {
            assertNull(RuleStatus.fromCode(null));
        }

        @Test
        @DisplayName("空字符串返回 null")
        void shouldReturnNullForBlank() {
            assertNull(RuleStatus.fromCode(""));
            assertNull(RuleStatus.fromCode("   "));
        }

        @Test
        @DisplayName("非法编码返回 null")
        void shouldReturnNullForInvalid() {
            assertNull(RuleStatus.fromCode("UNKNOWN"));
            assertNull(RuleStatus.fromCode("ACTIVE"));
            assertNull(RuleStatus.fromCode("123"));
        }

        @Test
        @DisplayName("getDesc 返回中文描述")
        void shouldReturnDesc() {
            assertEquals("草稿", RuleStatus.DRAFT.getDesc());
            assertEquals("待审核", RuleStatus.REVIEW.getDesc());
            assertEquals("已发布", RuleStatus.PUBLISHED.getDesc());
            assertEquals("已停用", RuleStatus.DISABLED.getDesc());
            assertEquals("已归档", RuleStatus.ARCHIVED.getDesc());
        }
    }
}
