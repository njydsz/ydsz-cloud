package com.njydsz.userinfo.domain.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserImportResultDTO} 单元测试。
 *
 * <p>覆盖：静态工厂 of、字段映射、继承自 ExcelImportResult 的字段。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("用户导入结果 DTO")
class UserImportResultDTOTest {

  @Nested
  @DisplayName("of 静态工厂")
  class Factory {

    @Test
    @DisplayName("of 正确映射所有字段")
    void factoryMapsAllFields() {
      UserImportResultDTO dto = UserImportResultDTO.of(100, 80, 20, "line 3: username empty");
      assertThat(dto.getTotalCount()).isEqualTo(100);
      assertThat(dto.getSuccessCount()).isEqualTo(80);
      assertThat(dto.getFailCount()).isEqualTo(20);
      assertThat(dto.getFailDetails()).isEqualTo("line 3: username empty");
    }

    @Test
    @DisplayName("of 允许 failDetails 为 null")
    void factoryAllowsNullDetails() {
      UserImportResultDTO dto = UserImportResultDTO.of(10, 10, 0, null);
      assertThat(dto.getFailDetails()).isNull();
    }

    @Test
    @DisplayName("of 允许零计数")
    void factoryAllowsZeroCounts() {
      UserImportResultDTO dto = UserImportResultDTO.of(0, 0, 0, "");
      assertThat(dto.getTotalCount()).isZero();
      assertThat(dto.getSuccessCount()).isZero();
      assertThat(dto.getFailCount()).isZero();
    }
  }

  @Nested
  @DisplayName("构造器与 setter")
  class Constructors {

    @Test
    @DisplayName("无参构造器字段为 null/0")
    void noArgsConstructor() {
      UserImportResultDTO dto = new UserImportResultDTO();
      assertThat(dto.getFailDetails()).isNull();
      assertThat(dto.getTotalCount()).isZero();
      assertThat(dto.getSuccessCount()).isZero();
      assertThat(dto.getFailCount()).isZero();
    }

    @Test
    @DisplayName("builder 设置 failDetails")
    void builderSetsFailDetails() {
      UserImportResultDTO dto = UserImportResultDTO.builder()
          .totalCount(50)
          .successCount(40)
          .failCount(10)
          .failDetails("details")
          .build();
      assertThat(dto.getTotalCount()).isEqualTo(50);
      assertThat(dto.getSuccessCount()).isEqualTo(40);
      assertThat(dto.getFailCount()).isEqualTo(10);
      assertThat(dto.getFailDetails()).isEqualTo("details");
    }

    @Test
    @DisplayName("setter 可修改字段")
    void settersWork() {
      UserImportResultDTO dto = new UserImportResultDTO();
      dto.setTotalCount(5);
      dto.setSuccessCount(3);
      dto.setFailCount(2);
      dto.setFailDetails("some detail");
      assertThat(dto.getTotalCount()).isEqualTo(5);
      assertThat(dto.getSuccessCount()).isEqualTo(3);
      assertThat(dto.getFailCount()).isEqualTo(2);
      assertThat(dto.getFailDetails()).isEqualTo("some detail");
    }
  }
}
