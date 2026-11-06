package com.njydsz.cronjob.domain.dto;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BatchResultDTO} 单元测试。
 *
 * <p>覆盖：静态工厂（allSuccess/of）、ItemResult 静态工厂（success/failure）、getter/setter。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("批量操作结果 DTO")
class BatchResultDTOTest {

  @Nested
  @DisplayName("allSuccess 工厂")
  class AllSuccess {

    @Test
    @DisplayName("total=N，successCount=N，failureCount=0，details 为空")
    void allSuccessCounts() {
      BatchResultDTO<String> r = BatchResultDTO.allSuccess(5);
      assertThat(r.getTotal()).isEqualTo(5);
      assertThat(r.getSuccessCount()).isEqualTo(5);
      assertThat(r.getFailureCount()).isZero();
      assertThat(r.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("total=0 时全部计数为 0")
    void allSuccessZero() {
      BatchResultDTO<String> r = BatchResultDTO.allSuccess(0);
      assertThat(r.getTotal()).isZero();
      assertThat(r.getSuccessCount()).isZero();
      assertThat(r.getFailureCount()).isZero();
    }
  }

  @Nested
  @DisplayName("of 工厂")
  class Of {

    @Test
    @DisplayName("failureCount = total - successCount")
    void computesFailureCount() {
      BatchResultDTO<String> r = BatchResultDTO.of(10, 7);
      assertThat(r.getTotal()).isEqualTo(10);
      assertThat(r.getSuccessCount()).isEqualTo(7);
      assertThat(r.getFailureCount()).isEqualTo(3);
      assertThat(r.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("successCount=total 时 failureCount=0")
    void allSucceed() {
      BatchResultDTO<String> r = BatchResultDTO.of(4, 4);
      assertThat(r.getFailureCount()).isZero();
    }

    @Test
    @DisplayName("successCount=0 时 failureCount=total")
    void allFail() {
      BatchResultDTO<String> r = BatchResultDTO.of(3, 0);
      assertThat(r.getFailureCount()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("ItemResult 静态工厂")
  class ItemResultFactory {

    @Test
    @DisplayName("success 工厂：isSuccess=true，error=null")
    void successFactory() {
      BatchResultDTO.ItemResult<String> r = BatchResultDTO.ItemResult.success("job-001");
      assertThat(r.getItem()).isEqualTo("job-001");
      assertThat(r.isSuccess()).isTrue();
      assertThat(r.getError()).isNull();
    }

    @Test
    @DisplayName("failure 工厂：isSuccess=false，error=给定原因")
    void failureFactory() {
      BatchResultDTO.ItemResult<String> r = BatchResultDTO.ItemResult.failure("job-002", "任务不存在");
      assertThat(r.getItem()).isEqualTo("job-002");
      assertThat(r.isSuccess()).isFalse();
      assertThat(r.getError()).isEqualTo("任务不存在");
    }
  }

  @Nested
  @DisplayName("getter/setter")
  class Accessors {

    @Test
    @DisplayName("setter 写入后可读取")
    void setters() {
      BatchResultDTO<String> r = new BatchResultDTO<>();
      List<BatchResultDTO.ItemResult<String>> details =
          List.of(BatchResultDTO.ItemResult.success("a"));
      r.setTotal(1);
      r.setSuccessCount(1);
      r.setFailureCount(0);
      r.setDetails(details);
      assertThat(r.getTotal()).isEqualTo(1);
      assertThat(r.getSuccessCount()).isEqualTo(1);
      assertThat(r.getFailureCount()).isZero();
      assertThat(r.getDetails()).isEqualTo(details);
    }

    @Test
    @DisplayName("全参构造器赋值")
    void allArgsConstructor() {
      List<BatchResultDTO.ItemResult<String>> details =
          List.of(BatchResultDTO.ItemResult.failure("b", "err"));
      BatchResultDTO<String> r = new BatchResultDTO<>(2, 1, 1, details);
      assertThat(r.getTotal()).isEqualTo(2);
      assertThat(r.getSuccessCount()).isEqualTo(1);
      assertThat(r.getFailureCount()).isEqualTo(1);
      assertThat(r.getDetails()).hasSize(1);
    }
  }
}
