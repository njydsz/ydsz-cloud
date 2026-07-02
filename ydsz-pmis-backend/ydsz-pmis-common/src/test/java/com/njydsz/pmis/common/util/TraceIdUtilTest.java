package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TraceId 工具单元测试
 *
 * <p>覆盖 traceId 生成、MDC 存取与常量定义。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("TraceIdUtil 链路追踪测试")
class TraceIdUtilTest {

    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    @Test
    @DisplayName("generate 应返回 16 位字符串")
    void generate_lengthIs16() {
        String id = TraceIdUtil.generate();
        assertThat(id).hasSize(16);
    }

    @Test
    @DisplayName("generate 多次调用应返回不同 ID")
    void generate_distinct() {
        String a = TraceIdUtil.generate();
        String b = TraceIdUtil.generate();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("set/get 应能存取 traceId")
    void setAndGet() {
        TraceIdUtil.set("abc123");
        assertThat(TraceIdUtil.get()).isEqualTo("abc123");
    }

    @Test
    @DisplayName("未设置时 get 返回空字符串")
    void get_empty() {
        assertThat(TraceIdUtil.get()).isEmpty();
    }

    @Test
    @DisplayName("clear 后 get 返回空字符串")
    void clear_works() {
        TraceIdUtil.set("xyz");
        TraceIdUtil.clear();
        assertThat(TraceIdUtil.get()).isEmpty();
    }

    @Test
    @DisplayName("TRACE_ID_KEY 常量正确")
    void keyConstant() {
        assertThat(TraceIdUtil.TRACE_ID_KEY).isEqualTo("traceId");
    }
}
