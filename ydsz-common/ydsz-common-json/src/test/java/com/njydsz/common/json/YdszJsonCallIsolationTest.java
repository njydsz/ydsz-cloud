package com.njydsz.common.json;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link YdszJson} 跨调用数据隔离测试。
 *
 * <p>{@link #fromJson(String, Class)} 是全平台唯一的 JSON 出入口，各模块的 DTO 多为
 * Lombok {@code @Data @Builder} 形态：BeanReader 路径失败后会降级到 Builder +
 * {@code JsonParserUtil}，而后者复用线程级 {@code char[]}。历史上该降级链会把
 * <b>同线程上一次解析</b>的残留字符当作本次输入，导致截断报文解析出上一条报文的字段值。
 *
 * <p>本用例把"一次调用只读本次输入"钉在公开 API 边界上：解析器内部怎么复用缓冲区是实现细节，
 * 但返回对象不得包含上一次调用的数据。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@DisplayName("YdszJson 跨调用数据隔离")
class YdszJsonCallIsolationTest {

  /** 长报文：解析后其字符会残留在线程级缓冲区尾部 */
  private static final String LONG_JSON =
      "{\"code\":\"R_1\",\"name\":\"回滚前自动备份的规则名称\","
          + "\"conditionExpression\":\"amount > 100\"}";

  @Test
  @DisplayName("截断报文不得解析出上一次调用的字段值")
  void should_not_return_previous_call_values_for_truncated_input() {
    Snapshot first = YdszJson.fromJson(LONG_JSON, Snapshot.class);
    assertThat(first.getCode()).isEqualTo("R_1");
    assertThat(first.getConditionExpression()).isEqualTo("amount > 100");

    Snapshot second = YdszJson.fromJson("{\"code\":", Snapshot.class);

    assertThat(second).isNotNull();
    assertThat(second.getCode()).isNull();
    assertThat(second.getName()).isNull();
    assertThat(second.getConditionExpression()).isNull();
  }

  @Test
  @DisplayName("短报文只含自身字段")
  void should_return_only_own_fields_for_shorter_valid_payload() {
    YdszJson.fromJson(LONG_JSON, Snapshot.class);

    Snapshot shortPayload = YdszJson.fromJson("{\"code\":\"R_2\"}", Snapshot.class);

    assertThat(shortPayload.getCode()).isEqualTo("R_2");
    assertThat(shortPayload.getName()).isNull();
    assertThat(shortPayload.getConditionExpression()).isNull();
  }

  /**
   * 与业务 DTO 同形态的载体（{@code @Data @Builder @NoArgsConstructor}），
   * 用于触发 Builder 降级路径。
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  static class Snapshot {

    private String code;

    private String name;

    private String conditionExpression;
  }
}
