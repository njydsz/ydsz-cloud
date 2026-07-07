package com.njydsz.pmis.cronjob.core.dag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link FailStrategy} 单元测试（P4-3 DAG 工作流）。
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
}
