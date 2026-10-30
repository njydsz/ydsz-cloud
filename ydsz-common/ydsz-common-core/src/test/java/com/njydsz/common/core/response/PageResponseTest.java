package com.njydsz.common.core.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.core.code.YdszResultCode;

/**
 * {@link PageResponse} 与 {@link YdszResponse} 的契约钉住测试。
 *
 * <p>本类存在的理由：`ydsz-common-core` 是全平台唯一提供响应信封与测试基类的模块，却**零测试**；
 * 而"分页字段错位"已经在本仓真实发生过（`ydsz-literule-infra` 的版本分页把 `pageNum`/`pageSize`
 * 传反，见报告 B1-3）。`PageResponse.success(Long total, Long pageNum, Long pageSize, T data)`
 * 是四个同类型参数，编译器不会帮任何忙 —— 只能靠测试把"第几个参数落到哪个字段"钉死。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@DisplayName("统一响应信封")
class PageResponseTest {

  @Nested
  @DisplayName("偏移量分页")
  class OffsetMode {

    @Test
    @DisplayName("四个参数各自落到正确字段（防形参错位）")
    void success_should_map_each_argument_to_its_field() {
      List<String> rows = List.of("a", "b");

      PageResponse<List<String>> page = PageResponse.success(100L, 2L, 10L, rows);

      assertThat(page.getTotal()).isEqualTo(100L);
      assertThat(page.getPageNum()).isEqualTo(2L);
      assertThat(page.getPageSize()).isEqualTo(10L);
      assertThat(page.getData()).containsExactly("a", "b");
      assertThat(page.getCode()).isEqualTo(YdszResultCode.SUCCESS.getCode());
      assertThat(page.getMsg()).isNotBlank();
    }

    @Test
    @DisplayName("总页数：整除、有余数、非法页长、缺 total")
    void should_compute_pages() {
      assertThat(PageResponse.success(100L, 1L, 10L, null).getPages()).isEqualTo(10);
      assertThat(PageResponse.success(101L, 1L, 10L, null).getPages()).isEqualTo(11);
      assertThat(PageResponse.success(0L, 1L, 10L, null).getPages()).isZero();
      assertThat(PageResponse.success(100L, 1L, 0L, null).getPages()).isZero();
      assertThat(PageResponse.success(null, 1L, 10L, null).getPages()).isZero();
    }

    @Test
    @DisplayName("空分页：total=0、data=null，但仍是成功码")
    void empty_should_be_success_with_zero_total() {
      PageResponse<String> page = PageResponse.empty(3L, 20L);

      assertThat(page.getTotal()).isZero();
      assertThat(page.getData()).isNull();
      assertThat(page.getPageNum()).isEqualTo(3L);
      assertThat(page.getPageSize()).isEqualTo(20L);
      assertThat(page.getCode()).isEqualTo(YdszResultCode.SUCCESS.getCode());
    }

    @Test
    @DisplayName("失败响应携带结果码，且不伪造分页字段")
    void error_should_carry_result_code() {
      PageResponse<String> page = PageResponse.error(YdszResultCode.BAD_REQUEST);

      assertThat(page.getCode()).isEqualTo(YdszResultCode.BAD_REQUEST.getCode());
      assertThat(page.getMsg()).isNotBlank();
      assertThat(page.getTotal()).isNull();
    }

    @Test
    @DisplayName("偏移量响应不是游标模式")
    void offset_response_should_not_be_cursor_mode() {
      assertThat(PageResponse.success(1L, 1L, 10L, List.of()).isCursorMode()).isFalse();
    }
  }

  @Nested
  @DisplayName("游标分页")
  class CursorMode {

    @Test
    @DisplayName("有下一页游标：isMore=true 且进入游标模式")
    void ofList_with_cursor_should_mark_more() {
      PageResponse<List<String>> page = PageResponse.ofList(List.of("x"), "cursor-2");

      assertThat(page.getData()).containsExactly("x");
      assertThat(page.getNextCursor()).isEqualTo("cursor-2");
      assertThat(page.getIsMore()).isTrue();
      assertThat(page.isCursorMode()).isTrue();
    }

    @Test
    @DisplayName("末页：nextCursor=null 时 isMore=false，仍是游标模式")
    void ofList_without_cursor_should_mark_last_page() {
      PageResponse<List<String>> page = PageResponse.ofList(List.of(), null);

      assertThat(page.getNextCursor()).isNull();
      assertThat(page.getIsMore()).isFalse();
      assertThat(page.isCursorMode()).isTrue();
    }

    @Test
    @DisplayName("空游标响应：data=null、isMore=false")
    void empty_cursor_should_carry_no_data() {
      PageResponse<String> page = PageResponse.emptyCursor();

      assertThat(page.getData()).isNull();
      assertThat(page.getIsMore()).isFalse();
      assertThat(page.isCursorMode()).isTrue();
    }
  }

  @Nested
  @DisplayName("普通响应")
  class Envelope {

    @Test
    @DisplayName("成功：code=A00000、data 透传；error 与 success 码不同")
    void success_and_error_should_differ() {
      YdszResponse<String> ok = YdszResponse.success("payload");
      YdszResponse<String> bad = YdszResponse.error("A04000", "参数错误");

      assertThat(ok.getCode()).isEqualTo(YdszResponse.SUCCESS);
      assertThat(ok.getData()).isEqualTo("payload");
      assertThat(bad.getCode()).isEqualTo("A04000");
      assertThat(bad.getMsg()).isEqualTo("参数错误");
      assertThat(bad.getData()).isNull();
    }

    @Test
    @DisplayName("无参 success 的 data 为 null，msg 非空")
    void success_without_data() {
      YdszResponse<Object> ok = YdszResponse.success();

      assertThat(ok.getData()).isNull();
      assertThat(ok.getMsg()).isNotBlank();
    }
  }
}
