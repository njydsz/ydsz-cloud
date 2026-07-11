package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.api.BizErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BizException} 业务异常测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("BizException 业务异常测试")
class BizExceptionTest {

    @Nested
    @DisplayName("构造方法")
    class ConstructorTest {

        @Test
        @DisplayName("BizException(BizErrorCode) 正确设置 code 和 message")
        void shouldCreateWithBizErrorCode() {
            BizException ex = new BizException(BizErrorCode.UNAUTHORIZED);
            assertEquals(BizErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
            assertEquals(BizErrorCode.UNAUTHORIZED.getMessage(), ex.getErrorMessage());
            assertNull(ex.getArgs());
        }

        @Test
        @DisplayName("BizException(BizErrorCode, message) 覆盖消息")
        void shouldCreateWithCustomMessage() {
            BizException ex = new BizException(BizErrorCode.NOT_FOUND, "项目不存在");
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
            assertEquals("项目不存在", ex.getErrorMessage());
        }

        @Test
        @DisplayName("BizException(BizErrorCode, messageKey, args) 设置 i18n 参数")
        void shouldCreateWithI18nArgs() {
            BizException ex = new BizException(BizErrorCode.NOT_FOUND, "error.project_not_found", "PRJ-001");
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
            assertEquals("error.project_not_found", ex.getErrorMessage());
            assertNotNull(ex.getArgs());
            assertEquals(1, ex.getArgs().length);
            assertEquals("PRJ-001", ex.getArgs()[0]);
        }

        @Test
        @DisplayName("BizException(int code, String message) 直接指定码")
        void shouldCreateWithRawCode() {
            BizException ex = new BizException(500, "自定义错误");
            assertEquals(500, ex.getCode());
            assertEquals("自定义错误", ex.getErrorMessage());
        }

        @Test
        @DisplayName("BizException(String message) 默认使用 INTERNAL_ERROR 码")
        void shouldDefaultToInternalErrorCode() {
            BizException ex = new BizException("出错了");
            assertEquals(BizErrorCode.INTERNAL_ERROR.getCode(), ex.getCode());
            assertEquals("出错了", ex.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("异常链")
    class ChainingTest {

        @Test
        @DisplayName("BizException 是 RuntimeException")
        void shouldBeRuntimeException() {
            BizException ex = new BizException("test");
            assertInstanceOf(RuntimeException.class, ex);
        }

        @Test
        @DisplayName("可以被 try-catch 捕获")
        void shouldBeCatchable() {
            assertThrows(BizException.class, () -> {
                throw new BizException(BizErrorCode.UNAUTHORIZED);
            });
        }
    }
}
