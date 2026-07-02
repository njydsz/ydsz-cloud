package com.njydsz.pmis.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 公共常量单元测试
 *
 * <p>验证 Header、逻辑删除、业务状态与 MDC 键值符合规范。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("CommonConstants 常量测试")
class CommonConstantsTest {

    @Test
    @DisplayName("Header 常量值应符合规范")
    void headers() {
        assertThat(CommonConstants.HEADER_TRACE_ID).isEqualTo("X-Trace-Id");
        assertThat(CommonConstants.HEADER_USER_ID).isEqualTo("X-User-Id");
        assertThat(CommonConstants.HEADER_USERNAME).isEqualTo("X-Username");
        assertThat(CommonConstants.HEADER_USER_DEPT).isEqualTo("X-User-Dept-Id");
    }

    @Test
    @DisplayName("逻辑删除状态值正确")
    void deletedFlag() {
        assertThat(CommonConstants.NOT_DELETED).isEqualTo(0);
        assertThat(CommonConstants.DELETED).isEqualTo(1);
    }

    @Test
    @DisplayName("业务状态值正确")
    void statusValues() {
        assertThat(CommonConstants.STATUS_ENABLED).isEqualTo("ENABLED");
        assertThat(CommonConstants.STATUS_DISABLED).isEqualTo("DISABLED");
        assertThat(CommonConstants.STATUS_DRAFT).isEqualTo("DRAFT");
        assertThat(CommonConstants.STATUS_ACTIVE).isEqualTo("ACTIVE");
        assertThat(CommonConstants.STATUS_FINISHED).isEqualTo("FINISHED");
    }

    @Test
    @DisplayName("MDC 键与 Header 键保持一致")
    void mdcAndHeaderAligned() {
        assertThat(CommonConstants.MDC_TRACE_ID).isEqualTo("traceId");
    }
}
