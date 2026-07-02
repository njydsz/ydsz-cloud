package com.njydsz.pmis.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一响应 Result 单元测试
 *
 * <p>覆盖 {@code ok/failed/isSuccess} 等工厂方法与状态判定逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("Result 统一响应测试")
class ResultTest {

    @Test
    @DisplayName("ok() 应返回 code=0、message=ok、data=null")
    void ok_returnsSuccess() {
        Result<String> r = Result.ok();
        assertThat(r.getCode()).isEqualTo(0);
        assertThat(r.getMessage()).isEqualTo("ok");
        assertThat(r.getData()).isNull();
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getTimestamp()).isGreaterThan(0);
    }

    @Test
    @DisplayName("ok(data) 应返回 code=0 并携带 data")
    void okWithData_returnsData() {
        Result<String> r = Result.ok("hello");
        assertThat(r.getCode()).isEqualTo(0);
        assertThat(r.getData()).isEqualTo("hello");
        assertThat(r.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("ok(data, message) 应返回自定义 message")
    void okWithDataAndMessage() {
        Result<String> r = Result.ok("hello", "操作成功");
        assertThat(r.getData()).isEqualTo("hello");
        assertThat(r.getMessage()).isEqualTo("操作成功");
    }

    @Test
    @DisplayName("failed(code, message) 应返回 code & message")
    void failedWithCodeAndMessage() {
        Result<Void> r = Result.failed(10001, "参数错误");
        assertThat(r.getCode()).isEqualTo(10001);
        assertThat(r.getMessage()).isEqualTo("参数错误");
        assertThat(r.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("failed(BizErrorCode) 应使用枚举 code 与 message")
    void failedWithEnum() {
        Result<Void> r = Result.failed(BizErrorCode.NOT_FOUND);
        assertThat(r.getCode()).isEqualTo(10101);
        assertThat(r.getMessage()).isEqualTo("资源不存在");
    }
}
