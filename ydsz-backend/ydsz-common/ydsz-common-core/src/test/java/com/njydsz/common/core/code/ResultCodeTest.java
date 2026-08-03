package com.njydsz.common.core.code;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ResultCode} 默认实现测试
 *
 * <p>覆盖 code 前缀推断 HTTP 状态码、messageKey 默认实现等行为。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@DisplayName("ResultCode 接口默认实现测试")
class ResultCodeTest {

    @Test
    @DisplayName("A2 前缀推断为 401")
    void a2_prefix401() {
        assertEquals(401, TestCode.A2_001.getHttpStatusCode());
        assertEquals(401, TestCode.A2_101.getHttpStatusCode());
    }

    @Test
    @DisplayName("A 前缀（非 A2）推断为 400")
    void a_prefix400() {
        assertEquals(400, TestCode.A1_001.getHttpStatusCode());
        assertEquals(400, TestCode.A1_101.getHttpStatusCode());
        assertEquals(400, TestCode.A1_301.getHttpStatusCode());
    }

    @Test
    @DisplayName("B 前缀推断为 500")
    void b_prefix500() {
        assertEquals(500, TestCode.B1_201.getHttpStatusCode());
        assertEquals(500, TestCode.B2_001.getHttpStatusCode());
    }

    @Test
    @DisplayName("C 前缀推断为 500")
    void c_prefix500() {
        assertEquals(500, TestCode.C1_501.getHttpStatusCode());
    }

    @Test
    @DisplayName("A00000 推断为 200")
    void success200() {
        assertEquals(200, TestCode.SUCCESS.getHttpStatusCode());
    }

    @Test
    @DisplayName("其他前缀推断为 500")
    void otherPrefix500() {
        assertEquals(500, TestCode.X1_001.getHttpStatusCode());
        assertEquals(500, TestCode.D1_001.getHttpStatusCode());
    }

    @Test
    @DisplayName("null code 推断为 500")
    void nullCode500() {
        assertEquals(500, TestCode.NULL_CODE.getHttpStatusCode());
    }

    @Test
    @DisplayName("getMessageKey 默认返回 error.枚举名")
    void messageKey() {
        assertEquals("error.A1_001", TestCode.A1_001.getMessageKey());
    }

    @Test
    @DisplayName("BaseResultCode 覆盖了默认实现（与接口推断一致）")
    void baseResultCodeConsistency() {
        // 抽查：BaseResultCode 的显式映射应与接口默认推断一致
        assertEquals(400, BaseResultCode.BAD_REQUEST.getHttpStatusCode());
        assertEquals(401, BaseResultCode.UNAUTHORIZED.getHttpStatusCode());
        assertEquals(500, BaseResultCode.INTERNAL_ERROR.getHttpStatusCode());
        assertEquals(200, BaseResultCode.SUCCESS.getHttpStatusCode());
    }

    /**
     * 测试用 ResultCode 实现（不覆盖 getHttpStatusCode，验证默认推断）。
     */
    private enum TestCode implements ResultCode {
        SUCCESS("A00000"),
        A1_001("A10001"),
        A1_101("A10101"),
        A1_301("A10301"),
        A2_001("A20001"),
        A2_101("A20101"),
        B1_201("B10201"),
        B2_001("B20001"),
        C1_501("C10501"),
        X1_001("X10001"),
        D1_001("D10001"),
        NULL_CODE(null);

        private final String code;

        TestCode(String code) {
            this.code = code;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMsg() {
            return "test";
        }
    }
}
