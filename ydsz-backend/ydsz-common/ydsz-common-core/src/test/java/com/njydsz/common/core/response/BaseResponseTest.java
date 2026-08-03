package com.njydsz.common.core.response;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.constant.HeaderConstants;

/**
 * {@link BaseResponse} 单元测试
 *
 * <p>覆盖全部静态工厂方法、traceId 注入、国际化消息解析、isSuccess/isFailed、RFC 7807 等行为。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("BaseResponse 统一响应体测试")
class BaseResponseTest {

    @BeforeEach
    void setUp() {
        MDC.put(HeaderConstants.MDC_TRACE_ID_KEY, "test-trace-001");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        BaseResponse.setResolver(null);
    }

    @Test
    @DisplayName("success() 返回 A00000 且 data 为 null")
    void success_noArgs() {
        BaseResponse<Void> resp = BaseResponse.success();
        assertEquals(BaseResponse.SUCCESS, resp.getCode());
        assertNull(resp.getData());
        assertTrue(resp.isSuccess());
        assertFalse(resp.isFailed());
    }

    @Test
    @DisplayName("success(data) 携带数据返回")
    void success_withData() {
        BaseResponse<String> resp = BaseResponse.success("hello");
        assertEquals(BaseResponse.SUCCESS, resp.getCode());
        assertEquals("hello", resp.getData());
    }

    @Test
    @DisplayName("successMsg(msg) 使用自定义消息")
    void successMsg_customMessage() {
        BaseResponse<Void> resp = BaseResponse.successMsg("自定义成功消息");
        assertEquals(BaseResponse.SUCCESS, resp.getCode());
        assertEquals("自定义成功消息", resp.getMsg());
    }

    @Test
    @DisplayName("success(msg, data) 同时携带消息与数据")
    void success_msgAndData() {
        BaseResponse<Integer> resp = BaseResponse.success("成功", 42);
        assertEquals("成功", resp.getMsg());
        assertEquals(42, resp.getData());
    }

    @Test
    @DisplayName("error() 返回 A01001 错误码")
    void error_noArgs() {
        BaseResponse<Void> resp = BaseResponse.error();
        assertEquals(BaseResponse.ERROR, resp.getCode());
        assertFalse(resp.isSuccess());
        assertTrue(resp.isFailed());
    }

    @Test
    @DisplayName("error(msg) 携带自定义错误消息")
    void error_withMessage() {
        BaseResponse<Void> resp = BaseResponse.error("参数错误");
        assertEquals("参数错误", resp.getMsg());
        assertEquals(BaseResponse.ERROR, resp.getCode());
    }

    @Test
    @DisplayName("error(msg, data) 同时携带错误消息与数据")
    void error_msgAndData() {
        // 注意：两个 String 参数时 Java 会选择 error(String, String) 重载
        // 此处用 Integer data 明确触发 error(String msg, T data)
        BaseResponse<Integer> resp = BaseResponse.error("失败", 42);
        assertEquals("失败", resp.getMsg());
        assertEquals(42, resp.getData());
    }

    @Test
    @DisplayName("error(code, msg) 使用自定义错误码")
    void error_codeAndMsg() {
        BaseResponse<Void> resp = BaseResponse.error("B10001", "自定义错误");
        assertEquals("B10001", resp.getCode());
        assertEquals("自定义错误", resp.getMsg());
    }

    @Test
    @DisplayName("error(ResultCode) 使用标准结果码枚举")
    void error_withResultCode() {
        BaseResponse<Void> resp = BaseResponse.error(BaseResultCode.NOT_FOUND);
        assertEquals("A10101", resp.getCode());
        assertEquals("资源不存在", resp.getMsg());
    }

    @Test
    @DisplayName("error(ResultCode, msg) 自定义消息覆盖枚举默认消息")
    void error_resultCodeCustomMsg() {
        BaseResponse<Void> resp = BaseResponse.error(BaseResultCode.RATE_LIMIT, "请 1 分钟后再试");
        assertEquals("A10301", resp.getCode());
        assertEquals("请 1 分钟后再试", resp.getMsg());
    }

    @Test
    @DisplayName("of(code, msg, data) 手动构建")
    void of_custom() {
        BaseResponse<String> resp = BaseResponse.of("X00001", "自定义", "value");
        assertEquals("X00001", resp.getCode());
        assertEquals("value", resp.getData());
    }

    @Test
    @DisplayName("构造函数自动注入 MDC 中的 traceId")
    void traceId_fromMdc() {
        BaseResponse<String> resp = BaseResponse.success("data");
        assertEquals("test-trace-001", resp.getTraceId());
    }

    @Test
    @DisplayName("MDC 无 traceId 时 traceId 为 null")
    void traceId_nullWhenMdcEmpty() {
        MDC.clear();
        BaseResponse<String> resp = BaseResponse.success("data");
        assertNull(resp.getTraceId());
    }

    @Test
    @DisplayName("timestamp 自动生成且非空")
    void timestamp_generated() {
        BaseResponse<String> resp = BaseResponse.success("data");
        assertNotNull(resp.getTimestamp());
        assertTrue(resp.getTimestamp() > 0);
    }

    @Test
    @DisplayName("设置 MessageResolver 后 success() 使用国际化消息")
    void i18n_resolverApplied() {
        BaseResponse.setResolver((key, defaultValue) -> "i18n-" + key);
        BaseResponse<Void> resp = BaseResponse.success();
        assertEquals("i18n-response.success", resp.getMsg());
    }

    @Test
    @DisplayName("MessageResolver 返回 null 时回退默认值")
    void i18n_resolverReturnsNull_fallbackToDefault() {
        BaseResponse.setResolver((key, defaultValue) -> null);
        BaseResponse<Void> resp = BaseResponse.success();
        assertEquals("操作成功", resp.getMsg());
    }

    @Test
    @DisplayName("未注册 resolver 时 isResolverRegistered 为 false")
    void i18n_notRegistered() {
        assertFalse(BaseResponse.isResolverRegistered());
    }

    @Test
    @DisplayName("注册 resolver 后 isResolverRegistered 为 true")
    void i18n_registered() {
        BaseResponse.setResolver((key, defaultValue) -> defaultValue);
        assertTrue(BaseResponse.isResolverRegistered());
    }

    @Test
    @DisplayName("errorWithDetail 返回携带 RFC 7807 ProblemDetail 的响应")
    void errorWithDetail_problemDetail() {
        BaseResponse<ProblemDetail> resp = BaseResponse.errorWithDetail(
                BaseResultCode.NOT_FOUND, "订单不存在");
        assertEquals("A10101", resp.getCode());
        assertNotNull(resp.getData());
        assertEquals(404, resp.getData().getStatus());
        assertEquals("订单不存在", resp.getData().getDetail());
        assertNotNull(resp.getData().getType());
        assertTrue(resp.getData().getType().toString().startsWith(ProblemDetail.DEFAULT_TYPE_PREFIX));
    }

    @Test
    @DisplayName("errorWithDetail 支持携带 instance URI")
    void errorWithDetail_withInstance() {
        BaseResponse<ProblemDetail> resp = BaseResponse.errorWithDetail(
                BaseResultCode.NOT_FOUND, "订单不存在", URI.create("/api/v1/orders/123"));
        assertEquals("/api/v1/orders/123", resp.getData().getInstance().toString());
    }

    @Test
    @DisplayName("serialVersionUID 存在")
    void serialVersionUid() throws Exception {
        java.lang.reflect.Field field = BaseResponse.class.getDeclaredField("serialVersionUID");
        field.setAccessible(true);
        assertEquals(1L, field.getLong(null));
    }

    @Test
    @DisplayName("success 与 error 的 code 常量与 BaseResultCode 一致")
    void constants_consistent() {
        assertEquals(BaseResultCode.SUCCESS.getCode(), BaseResponse.SUCCESS);
        assertNotEquals(BaseResponse.SUCCESS, BaseResponse.ERROR);
    }
}
