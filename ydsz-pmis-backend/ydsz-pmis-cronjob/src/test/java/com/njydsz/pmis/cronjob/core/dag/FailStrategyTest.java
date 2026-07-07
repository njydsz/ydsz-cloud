package com.njydsz.pmis.cronjob.core.dag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FailStrategy} 单元测试（P4-3 DAG 工作流，P2-6 增强）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("FailStrategy 失败传播策略测试")
class FailStrategyTest {

    @Test
    @DisplayName("parse null 返回 FAIL_FAST")
    void parse_null_returnsFailFast() {
        assertEquals(FailStrategy.FAIL_FAST, FailStrategy.parse(null));
    }

    @Test
    @DisplayName("parse 空字符串返回 FAIL_FAST")
    void parse_empty_returnsFailFast() {
        assertEquals(FailStrategy.FAIL_FAST, FailStrategy.parse(""));
    }

    @Test
    @DisplayName("parse 空白字符串返回 FAIL_FAST")
    void parse_blank_returnsFailFast() {
        assertEquals(FailStrategy.FAIL_FAST, FailStrategy.parse("   "));
    }

    @Test
    @DisplayName("parse 大小写不敏感")
    void parse_caseInsensitive() {
        assertEquals(FailStrategy.CONTINUE_ON_FAIL, FailStrategy.parse("continue_on_fail"));
        assertEquals(FailStrategy.CONTINUE_ON_FAIL, FailStrategy.parse("Continue_On_Fail"));
        assertEquals(FailStrategy.FAIL_FAST, FailStrategy.parse("fail_fast"));
    }

    @Test
    @DisplayName("parse 带空格自动 trim")
    void parse_withSpaces_trimmed() {
        assertEquals(FailStrategy.FAIL_FAST, FailStrategy.parse("  FAIL_FAST  "));
        assertEquals(FailStrategy.CONTINUE_ON_FAIL, FailStrategy.parse("  CONTINUE_ON_FAIL  "));
    }

    @Test
    @DisplayName("parse 无效值返回 FAIL_FAST")
    void parse_invalid_returnsFailFast() {
        assertEquals(FailStrategy.FAIL_FAST, FailStrategy.parse("INVALID"));
        assertEquals(FailStrategy.FAIL_FAST, FailStrategy.parse("SKIP"));
        assertEquals(FailStrategy.FAIL_FAST, FailStrategy.parse("abc123"));
    }

    // ==================== P2-6: 新增策略 ====================

    @Test
    @DisplayName("P2-6 parse RETRY 大小写不敏感")
    void parse_retry_caseInsensitive() {
        assertEquals(FailStrategy.RETRY, FailStrategy.parse("retry"));
        assertEquals(FailStrategy.RETRY, FailStrategy.parse("RETRY"));
        assertEquals(FailStrategy.RETRY, FailStrategy.parse("  Retry  "));
    }

    @Test
    @DisplayName("P2-6 parse SKIP_SUBSEQUENT 大小写不敏感")
    void parse_skipSubsequent_caseInsensitive() {
        assertEquals(FailStrategy.SKIP_SUBSEQUENT, FailStrategy.parse("skip_subsequent"));
        assertEquals(FailStrategy.SKIP_SUBSEQUENT, FailStrategy.parse("SKIP_SUBSEQUENT"));
        assertEquals(FailStrategy.SKIP_SUBSEQUENT, FailStrategy.parse("  Skip_Subsequent  "));
    }

    @Test
    @DisplayName("P2-6 shouldTriggerOnFailure: CONTINUE_ON_FAIL 返回 true")
    void shouldTriggerOnFailure_continueOnFail_true() {
        assertTrue(FailStrategy.CONTINUE_ON_FAIL.shouldTriggerOnFailure());
    }

    @Test
    @DisplayName("P2-6 shouldTriggerOnFailure: FAIL_FAST/RETRY/SKIP_SUBSEQUENT 返回 false")
    void shouldTriggerOnFailure_others_false() {
        assertFalse(FailStrategy.FAIL_FAST.shouldTriggerOnFailure());
        assertFalse(FailStrategy.RETRY.shouldTriggerOnFailure());
        assertFalse(FailStrategy.SKIP_SUBSEQUENT.shouldTriggerOnFailure());
    }
}
