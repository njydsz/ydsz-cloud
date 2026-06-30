package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.api.BizErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BizException 业务异常测试")
class BizExceptionTest {

    @Test
    @DisplayName("BizException(BizErrorCode) 应设置 code 与 message")
    void constructor_withEnum() {
        BizException e = new BizException(BizErrorCode.NOT_FOUND);
        assertThat(e.getCode()).isEqualTo(10101);
        assertThat(e.getMessage()).isEqualTo("资源不存在");
        assertThat(e.getErrorMessage()).isEqualTo("资源不存在");
    }

    @Test
    @DisplayName("BizException(BizErrorCode, message) 自定义 message 应覆盖默认")
    void constructor_withCustomMessage() {
        BizException e = new BizException(BizErrorCode.NOT_FOUND, "用户不存在");
        assertThat(e.getCode()).isEqualTo(10101);
        assertThat(e.getMessage()).isEqualTo("用户不存在");
    }

    @Test
    @DisplayName("BizException(code, message) 任意 code 应被保留")
    void constructor_withCodeAndMessage() {
        BizException e = new BizException(50000, "自定义错误");
        assertThat(e.getCode()).isEqualTo(50000);
        assertThat(e.getMessage()).isEqualTo("自定义错误");
    }
}
