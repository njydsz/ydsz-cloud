package com.njydsz.common.core.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link BaseResponse} 和 {@link PageResponse} 单元测试。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("BaseResponse 测试")
class BaseResponseTest {

    @AfterEach
    void cleanup() {
        BaseResponse.setResolver(null);
    }

    @Nested
    @DisplayName("成功响应")
    class SuccessResponse {

        @Test
        @DisplayName("success() 返回成功码和无数据")
        void success_noData() {
            BaseResponse<String> resp = BaseResponse.success();
            assertThat(resp.getCode()).isEqualTo(BaseResponse.SUCCESS);
            assertThat(resp.getData()).isNull();
            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.isFailed()).isFalse();
        }

        @Test
        @DisplayName("success(data) 返回成功码和数据")
        void success_withData() {
            BaseResponse<String> resp = BaseResponse.success("hello");
            assertThat(resp.getCode()).isEqualTo(BaseResponse.SUCCESS);
            assertThat(resp.getData()).isEqualTo("hello");
        }

        @Test
        @DisplayName("success(msg, data) 返回自定义消息和数据")
        void success_withMsgAndData() {
            BaseResponse<String> resp = BaseResponse.success("自定义成功", "data");
            assertThat(resp.getMsg()).isEqualTo("自定义成功");
            assertThat(resp.getData()).isEqualTo("data");
        }
    }

    @Nested
    @DisplayName("失败响应")
    class ErrorResponse {

        @Test
        @DisplayName("error(msg) 返回默认错误码和消息")
        void error_withMsg() {
            BaseResponse<String> resp = BaseResponse.error("参数错误");
            assertThat(resp.getCode()).isEqualTo(BaseResponse.ERROR);
            assertThat(resp.getMsg()).isEqualTo("参数错误");
            assertThat(resp.isFailed()).isTrue();
            assertThat(resp.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("error(code, msg) 返回自定义错误码和消息")
        void error_withCodeAndMsg() {
            BaseResponse<String> resp = BaseResponse.error("A10001", "请求参数错误");
            assertThat(resp.getCode()).isEqualTo("A10001");
            assertThat(resp.getMsg()).isEqualTo("请求参数错误");
        }

        @Test
        @DisplayName("error(ResultCode) 返回结果码")
        void error_withResultCode() {
            BaseResponse<String> resp = BaseResponse.error(BaseResultCode.BAD_REQUEST);
            assertThat(resp.getCode()).isEqualTo("A10001");
            assertThat(resp.getMsg()).isEqualTo("请求参数错误");
        }

        @Test
        @DisplayName("error(ResultCode, msg) 覆盖消息")
        void error_withResultCodeAndMsg() {
            BaseResponse<String> resp = BaseResponse.error(BaseResultCode.NOT_FOUND, "用户不存在");
            assertThat(resp.getCode()).isEqualTo("A10101");
            assertThat(resp.getMsg()).isEqualTo("用户不存在");
        }
    }

    @Nested
    @DisplayName("failed(Throwable) 异常信息保留")
    class FailedThrowable {

        @Test
        @DisplayName("error(String) 包含异常类名和消息")
        void error_withExceptionMessage() {
            BaseResponse<String> resp = BaseResponse.error("NullPointerException: null ref");
            assertThat(resp.getMsg()).contains("NullPointerException");
            assertThat(resp.getMsg()).contains("null ref");
        }

        @Test
        @DisplayName("error(String) 仅类名")
        void error_classNameOnly() {
            BaseResponse<String> resp = BaseResponse.error("IllegalStateException");
            assertThat(resp.getMsg()).contains("IllegalStateException");
        }
    }

    @Nested
    @DisplayName("国际化消息解析")
    class I18nResolver {

        @Test
        @DisplayName("设置 Resolver 后使用 Resolver 解析消息")
        void withResolver() {
            BaseResponse.setResolver((key, defaultValue) -> {
                if ("response.success".equals(key)) {
                    return "Operation succeeded";
                }
                return defaultValue;
            });
            BaseResponse<String> resp = BaseResponse.success();
            assertThat(resp.getMsg()).isEqualTo("Operation succeeded");
        }

        @Test
        @DisplayName("未设置 Resolver 时使用默认消息")
        void withoutResolver() {
            BaseResponse<String> resp = BaseResponse.success();
            assertThat(resp.getMsg()).isEqualTo("操作成功");
        }
    }

    @Nested
    @DisplayName("PageResponse")
    class PageResponseTest {

        @Test
        @DisplayName("success 构建分页响应")
        void success_withPagination() {
            List<String> data = List.of("a", "b", "c");
            PageResponse<List<String>> resp = PageResponse.success(100L, 2L, 10L, data);
            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getTotal()).isEqualTo(100L);
            assertThat(resp.getPageNum()).isEqualTo(2L);
            assertThat(resp.getPageSize()).isEqualTo(10L);
            assertThat(resp.getPages()).isEqualTo(10L);
        }

        @Test
        @DisplayName("总页数计算正确")
        void calcPages() {
            PageResponse<List<String>> resp = PageResponse.success(101L, 1L, 10L, List.of());
            assertThat(resp.getPages()).isEqualTo(11L);
        }

        @Test
        @DisplayName("空结果总页数为 0")
        void calcPages_empty() {
            PageResponse<List<String>> resp = PageResponse.success(0L, 1L, 10L, List.of());
            assertThat(resp.getPages()).isEqualTo(0L);
        }

        @Test
        @DisplayName("hasNext / hasPrevious")
        void hasNext_hasPrevious() {
            PageResponse<List<String>> resp = PageResponse.success(100L, 5L, 10L, List.of());
            assertThat(resp.hasNext()).isTrue();
            assertThat(resp.hasPrevious()).isTrue();

            PageResponse<List<String>> firstPage = PageResponse.success(100L, 1L, 10L, List.of());
            assertThat(firstPage.hasPrevious()).isFalse();

            PageResponse<List<String>> lastPage = PageResponse.success(100L, 10L, 10L, List.of());
            assertThat(lastPage.hasNext()).isFalse();
        }

        @Test
        @DisplayName("fail 构建失败分页响应")
        void fail_response() {
            PageResponse<List<String>> resp = PageResponse.fail("查询失败");
            assertThat(resp.isFailed()).isTrue();
            assertThat(resp.getTotal()).isEqualTo(0L);
            assertThat(resp.getData()).isNull();
        }

        @Test
        @DisplayName("success 构建分页响应并获取数据")
        void success_withData() {
            PageResponse<List<String>> resp = PageResponse.success(10L, 1L, 10L, List.of("a", "b"));
            assertThat(resp.getData()).containsExactly("a", "b");
        }

        @Test
        @DisplayName("data 为 null 时返回 null")
        void data_null() {
            PageResponse<List<String>> resp = PageResponse.success(10L, 1L, 10L, null);
            assertThat(resp.getData()).isNull();
        }
    }

    @Test
    @DisplayName("timestamp 和 traceId 自动填充")
    void autoFields() {
        BaseResponse<String> resp = BaseResponse.success("data");
        assertThat(resp.getTimestamp()).isNotNull();
        assertThat(resp.getTimestamp()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("getMessage 是 getMsg 的别名")
    void getMessage_alias() {
        BaseResponse<String> resp = BaseResponse.error("test error");
        assertThat(resp.getMessage()).isEqualTo(resp.getMsg());
    }
}
