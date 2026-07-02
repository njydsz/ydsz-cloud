package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.api.BizErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BizException 业务异常单元测试
 *
 * <p>覆盖基于 BizErrorCode、自定义 message 与任意 code 的构造方式。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
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
