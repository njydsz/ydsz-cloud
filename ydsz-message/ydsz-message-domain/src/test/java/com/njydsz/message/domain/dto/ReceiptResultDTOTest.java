package com.njydsz.message.domain.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.message.domain.enums.receipt.ReceiptStatusEnum;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ReceiptResultDTO} 单元测试。
 *
 * <p>覆盖：静态工厂 of 构造、Lombok getter/setter、equals/hashCode 语义。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("回执结果 DTO 单元测试")
class ReceiptResultDTOTest {

  @Nested
  @DisplayName("静态工厂 of")
  class OfFactory {

    @Test
    @DisplayName("of(status) 仅填充状态，其余为 null")
    void should_create_with_status_only() {
      ReceiptResultDTO dto = ReceiptResultDTO.of(ReceiptStatusEnum.DELIVERED);

      assertThat(dto.getStatus()).isEqualTo(ReceiptStatusEnum.DELIVERED);
      assertThat(dto.getProviderMsg()).isNull();
      assertThat(dto.getRawResponse()).isNull();
    }

    @Test
    @DisplayName("of(status, providerMsg) 填充状态与描述")
    void should_create_with_status_and_message() {
      ReceiptResultDTO dto = ReceiptResultDTO.of(ReceiptStatusEnum.FAILED, "REJECTED");

      assertThat(dto.getStatus()).isEqualTo(ReceiptStatusEnum.FAILED);
      assertThat(dto.getProviderMsg()).isEqualTo("REJECTED");
      assertThat(dto.getRawResponse()).isNull();
    }

    @Test
    @DisplayName("of 支持全部回执状态")
    void should_support_all_statuses() {
      for (ReceiptStatusEnum status : ReceiptStatusEnum.values()) {
        assertThat(ReceiptResultDTO.of(status).getStatus()).isEqualTo(status);
      }
    }
  }

  @Nested
  @DisplayName("getter/setter")
  class Accessors {

    @Test
    @DisplayName("setter 正确写入")
    void should_set_fields() {
      ReceiptResultDTO dto = new ReceiptResultDTO();
      dto.setStatus(ReceiptStatusEnum.READ);
      dto.setProviderMsg("DELIVERED");
      dto.setRawResponse("{\"code\":0}");

      assertThat(dto.getStatus()).isEqualTo(ReceiptStatusEnum.READ);
      assertThat(dto.getProviderMsg()).isEqualTo("DELIVERED");
      assertThat(dto.getRawResponse()).isEqualTo("{\"code\":0}");
    }

    @Test
    @DisplayName("无参构造字段为 null")
    void should_have_null_fields_by_default() {
      ReceiptResultDTO dto = new ReceiptResultDTO();

      assertThat(dto.getStatus()).isNull();
      assertThat(dto.getProviderMsg()).isNull();
      assertThat(dto.getRawResponse()).isNull();
    }
  }

  @Nested
  @DisplayName("equals/hashCode")
  class Equality {

    @Test
    @DisplayName("相同字段相等")
    void should_be_equal() {
      ReceiptResultDTO a = new ReceiptResultDTO(ReceiptStatusEnum.READ, "msg", "raw");
      ReceiptResultDTO b = new ReceiptResultDTO(ReceiptStatusEnum.READ, "msg", "raw");

      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("不同状态不等")
    void should_not_be_equal_for_different_status() {
      ReceiptResultDTO a = ReceiptResultDTO.of(ReceiptStatusEnum.READ);
      ReceiptResultDTO b = ReceiptResultDTO.of(ReceiptStatusEnum.CLICKED);

      assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("toString 包含字段信息")
    void should_to_string_contain_fields() {
      ReceiptResultDTO dto = ReceiptResultDTO.of(ReceiptStatusEnum.DELIVERED);

      assertThat(dto.toString()).contains("DELIVERED");
    }
  }
}
